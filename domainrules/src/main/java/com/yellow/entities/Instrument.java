package com.yellow.entities;

import com.yellow.enums.AssetClass;

import java.util.Objects;

public class Instrument {

    private final Long instrumentId;

    private final String symbol;

    private final String displayName;
    private final AssetClass assetClass;

    private final String quoteCurrency;

    private boolean tradable;

    public Instrument(Long instrumentId,
                      String symbol,
                      String displayName,
                      AssetClass assetClass,
                      String quoteCurrency,
                      boolean tradable) {

        this.instrumentId = Objects.requireNonNull(instrumentId, "instrumentId");
        this.symbol = requireText(symbol, "symbol");
        this.displayName = requireText(displayName, "displayName");
        this.assetClass = Objects.requireNonNull(assetClass, "assetClass");
        this.quoteCurrency = requireText(quoteCurrency, "quoteCurrency");
        this.tradable = tradable;
    }

    public boolean isTradable() {
        return tradable;
    }

    public void delist() {
        this.tradable = false;
    }

    public void relist() {
        this.tradable = true;
    }

    public Long instrumentId()      { return instrumentId; }
    public String symbol()          { return symbol; }
    public String displayName()     { return displayName; }
    public AssetClass assetClass()  { return assetClass; }
    public String quoteCurrency()   { return quoteCurrency; }

    private static String requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(what + " must not be blank");
        }
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Instrument that)) return false;
        return instrumentId.equals(that.instrumentId);
    }

    @Override
    public int hashCode() {
        return instrumentId.hashCode();
    }

    @Override
    public String toString() {
        return "Instrument{" + symbol + ", " + assetClass
                + ", tradable=" + tradable + "}";
    }
}