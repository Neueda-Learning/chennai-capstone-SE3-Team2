package com.yellow.trade.marketdata;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One trading day. The raw prices as traded; volume is absent on a day Fauxnance filled in. */
public record Candle(LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
                     Long volume) {
}
