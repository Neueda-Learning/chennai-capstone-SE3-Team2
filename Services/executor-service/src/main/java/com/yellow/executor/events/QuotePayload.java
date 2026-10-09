package com.yellow.executor.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

/** One quote on market-data. eventType is always QUOTE. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QuotePayload(
        String symbol,
        BigDecimal price,
        BigDecimal bid,
        BigDecimal ask,
        BigDecimal spreadBps,
        String currency,
        BigDecimal change,
        BigDecimal changePercent,
        BigDecimal previousClose,
        String marketState,
        boolean stale,
        Instant quoteAsOf) {
}
