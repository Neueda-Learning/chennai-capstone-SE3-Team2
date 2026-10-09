package com.yellow.trade.watchlists;

/**
 * The caps per account. An unbounded alert route would let one account fill
 * the table and turn the market-data consumer into the thing that takes the
 * order service down; past a cap the answer is LIM-409.
 */
final class WatchLimits {

    static final int WATCHLISTS = 5;
    static final int ITEMS_PER_WATCHLIST = 50;
    static final int ACTIVE_ALERTS = 20;

    private WatchLimits() {
    }
}
