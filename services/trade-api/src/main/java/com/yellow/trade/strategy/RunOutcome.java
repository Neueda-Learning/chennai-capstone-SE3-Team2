package com.yellow.trade.strategy;

/** What one run of a strategy came to. */
public enum RunOutcome {
    PLACED,
    FILLED,
    REJECTED,
    REFUSED_LIMIT,
    FAILED,
    STOPPED
}
