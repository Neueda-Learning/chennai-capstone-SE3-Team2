package com.yellow.trade.marketdata;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The latest price of one instrument. A fund's NAV is its price, with no bid,
 * ask or day change. stale says the source could not refresh it; an unpriced
 * instrument has no price at all and is stale.
 *
 * @param asOf when the price was observed, not when it was fetched
 */
public record PriceQuote(
        String symbol,
        BigDecimal price,
        BigDecimal change,
        BigDecimal changePercent,
        BigDecimal previousClose,
        BigDecimal bid,
        BigDecimal ask,
        String currency,
        Instant asOf,
        boolean stale) {

    /** An instrument we list but could not price. */
    public static PriceQuote unpriced(String symbol) {
        return new PriceQuote(symbol, null, null, null, null, null, null, "INR", null, true);
    }

    PriceQuote markedStale() {
        return stale ? this
                : new PriceQuote(symbol, price, change, changePercent, previousClose, bid, ask, currency, asOf, true);
    }
}
