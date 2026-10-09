package com.yellow.trade.marketdata;

/** A listed instrument with no chart: a fund, until the NAV service serves history. Answered as VAL-422. */
public class ChartUnavailableException extends RuntimeException {

    private final String symbol;

    public ChartUnavailableException(String symbol) {
        super("no chart for " + symbol);
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }
}
