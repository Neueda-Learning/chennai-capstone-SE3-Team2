package com.yellow.trade.instruments;

/**
 * @param symbol   what an order names: a ticker, or a fund's scheme code
 * @param type     STOCK, ETF or MF
 * @param exchange NSE or BSE; null for a mutual fund
 */
public record InstrumentResponse(String symbol, String name, String type, String exchange) {
}
