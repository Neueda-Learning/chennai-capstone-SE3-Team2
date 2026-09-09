package com.yellow.trade.mappers;

import com.yellow.enums.OrderStatus;

import java.time.Instant;
import java.util.List;

public interface OrderMapper {
    List<OrderHistoryRow> findByAccountId(Long accountId, OrderStatus status, Instant from, Instant to);
}