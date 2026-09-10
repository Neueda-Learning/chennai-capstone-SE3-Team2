package com.yellow.trade.dto;

import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * contracts/trade-api.yaml -> OrderHistoryEntry.
 *
 * Includes rejected orders: the order table is the audit trail, and an order
 * is recorded when it is received, before anyone knows whether it will
 * succeed.
 *
 * executedPrice is null until the order is FILLED -- a REJECTED or CANCELLED
 * order never has one. resolvedAt is null exactly when the status is NEW.
 */
public record OrderHistoryEntry(
        UUID orderId,
        Long accountId,
        String symbol,
        OrderSide side,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal executedPrice,
        OrderStatus status,
        String idempotencyKey,
        Instant placedAt,
        Instant resolvedAt) {
}
