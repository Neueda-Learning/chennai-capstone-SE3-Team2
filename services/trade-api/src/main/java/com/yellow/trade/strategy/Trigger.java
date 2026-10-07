package com.yellow.trade.strategy;

/**
 * What fires a strategy (decision log 0017 for the indicators).
 *
 * FALLS_THROUGH fires on a quote at or below its price; RISES_THROUGH at or above it.
 * MA_CROSSOVER fires a buy when the 20-day average crosses above the 50-day, a sell when it
 * crosses below. BOLLINGER fires a buy when the price reaches the lower band, a sell at the
 * upper. The indicators read daily closes, today's live price counted as today's close.
 */
public enum Trigger {
    FALLS_THROUGH,
    RISES_THROUGH,
    MA_CROSSOVER,
    BOLLINGER;

    /** A level trigger fires at a price the customer sets; an indicator trigger takes none. */
    boolean needsPrice() {
        return this == FALLS_THROUGH || this == RISES_THROUGH;
    }
}
