package com.yellow.trade.marketdata;

import java.util.List;

/**
 * @param range   1M, 3M, 6M, 1Y or 5Y
 * @param candles daily, oldest first
 */
public record CandleSeries(String symbol, String range, List<Candle> candles) {
}
