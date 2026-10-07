package com.yellow.trade.watchlists;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One entry, with the latest price market-data has carried for it.
 *
 * @param lastPrice null until a quote for it has arrived (a fund has no stream)
 * @param stale     the quote itself was flagged past its freshness window
 */
public record WatchlistItem(String symbol, int position, BigDecimal lastPrice, BigDecimal changePercent,
                            Instant priceAsOf, boolean stale) {
}
