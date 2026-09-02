package com.yellow.repositories;

import com.yellow.entities.Order;

public interface OrderRepository {
    boolean existsByAccountAndKey(Long accountId, String idempotencyKey);

    Order save(Order order);
}
