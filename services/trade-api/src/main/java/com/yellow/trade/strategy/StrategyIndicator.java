package com.yellow.trade.strategy;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * What an indicator strategy is waiting on, at the last quote seen (openapi/strategy.yaml):
 * the price, the 20-day and 50-day averages, and the Bollinger band. Null where the history
 * is too short.
 */
public record StrategyIndicator(BigDecimal price, Instant asOf, int days, BigDecimal shortAverage, BigDecimal longAverage,
                                BigDecimal lowerBand, BigDecimal upperBand) {

    static StrategyIndicator of(Indicators.View view) {
        return new StrategyIndicator(view.price(), view.asOf(), view.days(), Indicators.scaled(view.shortNow()),
                Indicators.scaled(view.longNow()), Indicators.scaled(view.lowerBand()), Indicators.scaled(view.upperBand()));
    }
}
