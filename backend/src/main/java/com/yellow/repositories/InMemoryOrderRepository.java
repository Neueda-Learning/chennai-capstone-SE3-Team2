package com.yellow.repositories;


import com.yellow.entities.Order;
import com.yellow.exceptions.DuplicateOrderException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class InMemoryOrderRepository implements OrderRepository {

    private record Key(Long accountId, String idempotencyKey) {
        Key {
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        }
    }

    private final Map<Long, Order> byId = new ConcurrentHashMap<>();
    private final Map<Key, Long> orderIdByKey = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @Override
    public boolean existsByAccountAndKey(Long accountId, String idempotencyKey) {
        if (accountId == null || idempotencyKey == null) {
            return false;
        }
        return orderIdByKey.containsKey(new Key(accountId, idempotencyKey));
    }

    @Override
    public Order save(Order order) {
        Objects.requireNonNull(order, "order");

        Order stored = order;

        if (order.orderId() == null) {
            stored = withId(order, nextId.getAndIncrement());

            if (stored.idempotencyKey() != null) {
                Key key = new Key(stored.accountId(), stored.idempotencyKey());
                Long previous = orderIdByKey.putIfAbsent(key, stored.orderId());
                if (previous != null) {
                    // What the unique index does in production.
                    throw new DuplicateOrderException();
                }
            }
        }

        byId.put(stored.orderId(), stored);
        return stored;
    }

    private static Order withId(Order order, Long orderId) {
        return new Order(
                orderId,
                order.accountId(),
                order.instrumentId(),
                order.side(),
                order.quantity(),
                order.limitPrice(),
                order.executedPrice(),
                order.status(),
                order.idempotencyKey(),
                order.placedAt(),
                order.resolvedAt());
    }

    public Optional<Order> findById(Long orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(orderId));
    }

    public Optional<Order> findByAccountAndKey(Long accountId, String idempotencyKey) {
        if (accountId == null || idempotencyKey == null) {
            return Optional.empty();
        }
        Long orderId = orderIdByKey.get(new Key(accountId, idempotencyKey));
        return orderId == null ? Optional.empty() : findById(orderId);
    }

    public List<Order> findByAccount(Long accountId) {
        return byId.values().stream()
                .filter(o -> o.accountId().equals(accountId))
                .collect(Collectors.toList());
    }

    public List<Order> findAll() {
        return List.copyOf(byId.values());
    }

    public int count() {
        return byId.size();
    }

    public void clear() {
        byId.clear();
        orderIdByKey.clear();
        nextId.set(1);
    }
}