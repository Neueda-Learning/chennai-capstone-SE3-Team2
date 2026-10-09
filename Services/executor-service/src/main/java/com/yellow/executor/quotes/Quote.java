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
     * A fund's NAV as a quote. A fund deals at one price, the same both ways,
     * so the NAV is the price, the bid and the ask.
     *
     * @param navDate the NAV's own date, carried as the market state
     */
    public static Quote ofNav(String symbol, BigDecimal nav, Instant asOf, String navDate, String source) {
        return ofNav(symbol, nav, asOf, navDate, source, false);
    }

    /** As above, for a NAV the service itself marked stale. It is still the price a fund deals at. */
    public static Quote ofNav(String symbol, BigDecimal nav, Instant asOf, String navDate, String source,
                              boolean stale) {
        return new Quote(symbol, nav, nav, nav, BigDecimal.ZERO, "INR", null, null, null,
                asOf, "NAV " + navDate, stale, source);
    }

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
