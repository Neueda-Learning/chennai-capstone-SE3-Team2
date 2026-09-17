package com.yellow.executor.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * What the executor puts on {@code trade-events} after every settled order.
 *
 * <p>Consumers downstream (analytics, notifications, portfolio) need enough
 * to answer "what happened and by how much did the account move", without
 * having to join back to Postgres. Every field that carries a price or quantity
 * comes from the same rounded calculation that drove the account update, so the
 * numbers here and the numbers in the database are identical by construction.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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
        Instant executedOn
) {}
