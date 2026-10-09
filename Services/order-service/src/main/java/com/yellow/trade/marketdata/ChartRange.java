package com.yellow.trade.marketdata;

import java.time.LocalDate;
import java.time.Period;
import java.util.Arrays;
import java.util.Optional;

/** How far back a chart reaches. Fauxnance serves daily candles only, so there is no intraday range. */
public enum ChartRange {

    ONE_MONTH("1M", Period.ofMonths(1)),
    THREE_MONTHS("3M", Period.ofMonths(3)),
    SIX_MONTHS("6M", Period.ofMonths(6)),
    ONE_YEAR("1Y", Period.ofYears(1)),
    FIVE_YEARS("5Y", Period.ofYears(5));

    private final String label;
    private final Period span;

    ChartRange(String label, Period span) {
        this.label = label;
        this.span = span;
    }

    public String label() {
        return label;
    }

    LocalDate from(LocalDate today) {
        return today.minus(span);
    }

    static Optional<ChartRange> of(String label) {
        return Arrays.stream(values()).filter(r -> r.label.equalsIgnoreCase(label)).findFirst();
    }
}
