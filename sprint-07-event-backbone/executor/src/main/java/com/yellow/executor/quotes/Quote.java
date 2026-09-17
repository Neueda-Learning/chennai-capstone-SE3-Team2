package com.yellow.executor.quotes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One Fauxnance quote, flattened from the API's {@code data} object plus the
 * two {@code meta} fields that describe it.
 *
 * <p>THREE PRICES, AND THE ONE YOU SETTLE AT IS NOT {@code price}. A buyer pays
 * {@code ask} and a seller receives {@code bid}; {@code price} is the last
 * observed trade and nobody transacts there. The API's own description says so.
 * Settling both sides at {@code price} would make a buy followed by a sell cost
 * nothing, so a strategy that trades often would look free when it is not. The
 * gap between bid and ask is the cost of trading and it is charged on every
 * round trip.
 *
 * <p>{@code stale} and {@code source} come from {@code meta}, and they mean
 * different things. {@code source} says where the numbers came from -- a real
 * stored observation, a cache hit, a live upstream, or generated. We do not
 * branch on it: {@code bid} and {@code ask} are modelled from candle history on
 * every quote whatever {@code source} says, so "refuse generated numbers" would
 * mean refusing to trade at all. {@code stale} is the one that matters: it
 * means the cached value is past its freshness window and no upstream could be
 * reached to refresh it, which is an outage wearing a 200.
 *
 * <p>{@code marketState} is carried through to {@code market-data} for
 * downstream consumers and is not branched on either -- Indian symbols report
 * {@code unknown}, so a rule built on it would be a rule built on noise.
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
