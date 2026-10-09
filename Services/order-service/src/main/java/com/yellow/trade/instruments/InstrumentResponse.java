package com.yellow.trade.instruments;

import com.yellow.trade.mappers.TradableInstrumentRow;

/**
 * @param symbol   what an order names: a ticker, or a fund's scheme code
 * @param type     STOCK, ETF or MF
 * @param exchange NSE or BSE; null for a mutual fund
 * @param tradable false for a delisted instrument, which only a symbols lookup returns
 */
public record InstrumentResponse(String symbol, String name, String type, String exchange, boolean tradable) {

    static InstrumentResponse from(TradableInstrumentRow row) {
        return new InstrumentResponse(row.getSymbol(), row.getName(), row.getInstrumentType(),
                row.getExchangeCode(), row.isTradable());
    }
}
