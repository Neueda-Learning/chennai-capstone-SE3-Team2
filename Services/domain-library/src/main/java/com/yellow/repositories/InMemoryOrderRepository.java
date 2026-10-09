package com.yellow.repositories;

import com.yellow.entities.Order;
import com.yellow.exceptions.DuplicateOrderException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryOrderRepository implements OrderRepository {

    private record Key(Long accountId, String idempotencyKey) {
        Key {
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        }
    }

    private final Map<UUID, Order> byId = new ConcurrentHashMap<>();
    private final Map<Key, UUID> orderIdByKey = new ConcurrentHashMap<>();

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

        // The order already carries its own UUID from Order.place() -- no id
        // assignment needed here anymore, only the duplicate-key bookkeeping.
        boolean isFirstSaveOfThisOrder = !byId.containsKey(order.orderId());

        if (isFirstSaveOfThisOrder && order.idempotencyKey() != null) {
            Key key = new Key(order.accountId(), order.idempotencyKey());
            UUID previous = orderIdByKey.putIfAbsent(key, order.orderId());
            if (previous != null) {
                // What the unique index does in production.
                throw new DuplicateOrderException(order.idempotencyKey(), previous);
            }
        }

        byId.put(order.orderId(), order);
        return order;
    }

    @Override
    public Optional<Order> findById(UUID orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(orderId));
    }

    public Optional<Order> findByAccountAndKey(Long accountId, String idempotencyKey) {
        if (accountId == null || idempotencyKey == null) {
            return Optional.empty();
        }
        UUID orderId = orderIdByKey.get(new Key(accountId, idempotencyKey));
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
    }
}