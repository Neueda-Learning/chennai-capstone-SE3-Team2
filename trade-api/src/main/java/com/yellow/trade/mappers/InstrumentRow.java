package com.yellow.trade.mappers;

/**
 * One tradeable instrument, flattened across the supertype and whichever
 * subtype holds its symbol.
 *
 * "Symbol" is equity.ticker for a stock or ETF and mutual_fund.scheme_code
 * for a fund. The contract exposes one field, so the join resolves which.
 */
public class InstrumentRow {

    private Long instrumentId;
    private String symbol;
    private String name;
    private String instrumentType;
    private boolean tradable;

    public Long getInstrumentId() { return instrumentId; }
    public void setInstrumentId(Long instrumentId) { this.instrumentId = instrumentId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    /** STOCK, ETF or MF. */
    public String getInstrumentType() { return instrumentType; }
    public void setInstrumentType(String instrumentType) { this.instrumentType = instrumentType; }

    /** False means delisted or suspended: no new orders, holdings stay visible. */
    public boolean isTradable() { return tradable; }
    public void setTradable(boolean tradable) { this.tradable = tradable; }
}
