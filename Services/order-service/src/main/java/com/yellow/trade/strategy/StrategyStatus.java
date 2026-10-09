package com.yellow.trade.strategy;

/** ARMED waits for its level; FIRED has placed its order; STOPPED was refused by a bound, or failed three times. */
public enum StrategyStatus {
    ARMED,
    FIRED,
    STOPPED
}
