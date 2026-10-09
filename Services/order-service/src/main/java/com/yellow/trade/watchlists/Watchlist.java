package com.yellow.trade.watchlists;

import java.util.List;

/** One watchlist as openapi/watchlists.yaml describes it, its entries in order. */
public record Watchlist(long id, String name, int position, List<WatchlistItem> items) {
}
