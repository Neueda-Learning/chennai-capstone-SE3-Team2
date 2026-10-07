package com.yellow.trade.events;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The trade-events payload (contracts/kafka-topics.md), field for field as the
 * executor publishes it for a fill or a rejection. The Trade REST API
 * publishes one kind: ORDER_CANCELLED.
 */
public record TradeEventPayload(
        String orderId,
        Long accountId,
        String symbol,
        String side,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal executedPrice,
        String status,
        String reason,
        BigDecimal cashDelta,
        BigDecimal positionQuantityAfter,
        BigDecimal averageCostAfter,
        Instant executedOn) {
}
