package com.yellow.trade.persistence;

import com.yellow.entities.Order;
import com.yellow.exceptions.DuplicateOrderException;
import com.yellow.repositories.OrderRepository;
import com.yellow.trade.mappers.OrderMapper;
import com.yellow.trade.mappers.OrderRow;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class MyBatisOrderRepository implements OrderRepository {

    private final OrderMapper orderMapper;

    public MyBatisOrderRepository(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public boolean existsByAccountAndKey(Long accountId, String idempotencyKey) {
        if (accountId == null || idempotencyKey == null) {
            return false;
        }
        // Deliberately a targeted read rather than a scan: it exists to give
        // the common case a clean ORD-409 with the ordering rule 8
        return orderMapper.findByAccountId(accountId, null, null, null).stream()
                .anyMatch(row -> idempotencyKey.equals(row.getIdempotencyKey()));
    }

    @Override
    public Order save(Order order) {
        OrderRow row = RowMapping.toRow(order);
        try {
            int affected = orderMapper.insert(row);
            if (affected != 1) {
                // Not reachable through a constraint -- that throws. This
                // catches a statement that silently matched nothing.
                throw new IllegalStateException(
                        "order insert affected " + affected + " rows, expected 1");
            }
        } catch (DuplicateKeyException e) {
            // The unique index refused it: another request won the race with
            // the same key. Same code the pre-check would have produced, so
            throw new DuplicateOrderException(order.idempotencyKey(), null);
        }
        return order;
    }

    @Override
    public Optional<Order> findById(UUID orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        OrderRow row = orderMapper.findById(orderId);
        return Optional.ofNullable(row).map(RowMapping::toOrder);
    }
}
