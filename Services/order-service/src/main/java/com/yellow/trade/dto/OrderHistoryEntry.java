package com.yellow.trade.dto;

import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderHistoryEntry(
        String orderId,
        Long accountId,
        String symbol,
        OrderSide side,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal executedPrice,
        OrderStatus status,
        String idempotencyKey,
        Instant createdOn) {
}
