package com.yellow.executor.quotes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One Fauxnance quote, flattened from the API's data object plus the two meta
 * fields that describe it.
 */
public record Quote(
        String symbol,
        BigDecimal price,
        BigDecimal bid,
        BigDecimal ask,
        BigDecimal spreadBps,
        String currency,
        BigDecimal change,
        BigDecimal changePercent,
        BigDecimal previousClose,
        Instant asOf,
        String marketState,
        boolean stale,
        String source) {

    /**
     * The price a BUY settles at. You buy at the offer.
     */
    public BigDecimal buyPrice() {
        return ask;
    }

    /**
     * The price a SELL settles at. You sell at the bid.
     */
    public BigDecimal sellPrice() {
        return bid;
    }
}
