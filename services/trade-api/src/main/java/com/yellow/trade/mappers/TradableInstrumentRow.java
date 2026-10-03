package com.yellow.trade.mappers;

/** One instrument a customer may order, as a picker lists it. */
public class TradableInstrumentRow {

    private String symbol;
    private String name;
    private String instrumentType;
    private String exchangeCode;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getInstrumentType() { return instrumentType; }
    public void setInstrumentType(String instrumentType) { this.instrumentType = instrumentType; }
    public String getExchangeCode() { return exchangeCode; }
    public void setExchangeCode(String exchangeCode) { this.exchangeCode = exchangeCode; }
}
