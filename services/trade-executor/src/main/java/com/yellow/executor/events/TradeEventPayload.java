package com.yellow.executor.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

/** What the executor puts on trade-events after every settled order. */
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
