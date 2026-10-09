package com.yellow.trade.notifications.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A price alert that a quote has crossed.
 *
 * @param eventId   the market-data quote's eventId: with the alert, what makes a replay a no-op
 * @param accountId whose alert it is
 * @param alertId   the watchlists module's alert, for the customer to find
 * @param symbol    the instrument, as an order names it
 * @param direction which way it was crossed
 * @param threshold the level the customer set
 * @param price     the price on the quote that crossed it
 * @param quoteAsOf when that price was observed, not when it was published
 */
public record AlertNotice(UUID eventId, long accountId, long alertId, String symbol, Direction direction,
                          BigDecimal threshold, BigDecimal price, Instant quoteAsOf) {

    public enum Direction {
        /** Rose to or through the threshold. */
        ABOVE,
        /** Fell to or through the threshold. */
        BELOW
    }
}
