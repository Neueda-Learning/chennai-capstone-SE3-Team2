package com.yellow.trade.mappers;

/** One instrument as a picker or a label lists it; tradable is false only for a delisted one. */
public class TradableInstrumentRow {

    private String symbol;
    private String name;
    private String instrumentType;
    private String exchangeCode;
    private boolean tradable;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getInstrumentType() { return instrumentType; }
    public void setInstrumentType(String instrumentType) { this.instrumentType = instrumentType; }
    public String getExchangeCode() { return exchangeCode; }
    public void setExchangeCode(String exchangeCode) { this.exchangeCode = exchangeCode; }
    public boolean isTradable() { return tradable; }
    public void setTradable(boolean tradable) { this.tradable = tradable; }
}
