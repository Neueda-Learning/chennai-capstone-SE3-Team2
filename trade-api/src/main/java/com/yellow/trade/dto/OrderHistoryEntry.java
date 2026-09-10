package com.yellow.trade.dto;

import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * contracts/trade-api.yaml -> OrderHistoryEntry.
 *
 * Every order recorded against the account, newest first, including rejected
 * and cancelled ones. This is the audit trail and it is never filtered by
 * default.
 *
 * executedPrice is null until the order is FILLED -- a REJECTED or CANCELLED
 * order never has one.
 *
 * quantity is decimal rather than int32, the single deviation recorded in
 * contracts/DEVIATIONS.md.
 */
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
