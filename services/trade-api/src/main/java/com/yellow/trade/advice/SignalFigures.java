package com.yellow.trade.advice;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The numbers behind a signal. An average or RSI is null where there is too
 * little history for it.
 *
 * @param lastPrice the latest price on market-data, else the last daily close
 * @param priceAsOf when that price was observed; null for a daily close
 * @param days      how many daily closes it was computed from, today's price counted as one
 */
public record SignalFigures(BigDecimal sma20, BigDecimal sma50, BigDecimal rsi14, BigDecimal lastPrice,
                            Instant priceAsOf, int days) {
}
