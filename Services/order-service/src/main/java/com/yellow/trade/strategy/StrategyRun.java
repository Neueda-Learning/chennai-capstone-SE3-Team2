package com.yellow.trade.strategy;

import java.math.BigDecimal;
import java.time.Instant;

/** One firing, refusal or outcome, for the customer to read. */
public record StrategyRun(long id, Instant at, BigDecimal quotePrice, RunOutcome outcome, String reason) {
}
