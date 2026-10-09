package com.yellow.repositories;

import com.yellow.entities.Order;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {
    boolean existsByAccountAndKey(Long accountId, String idempotencyKey);

    Order save(Order order);

    // Needed for cancelOrder
    Optional<Order> findById(UUID orderId);
}