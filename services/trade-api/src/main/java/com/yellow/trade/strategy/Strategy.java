package com.yellow.trade.strategy;

import com.yellow.enums.OrderSide;

import java.math.BigDecimal;
import java.time.Instant;

/** One strategy as openapi/strategy.yaml describes it. */
public record Strategy(long id, String symbol, OrderSide side, int quantity, Trigger trigger, BigDecimal triggerPrice,
                       BigDecimal maxSpend, int maxPosition, boolean enabled, StrategyStatus status, int failures,
                       Instant createdAt, Instant lastFiredAt, StrategyIndicator indicator) {
}
