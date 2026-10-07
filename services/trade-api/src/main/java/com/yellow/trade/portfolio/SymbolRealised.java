package com.yellow.trade.portfolio;

import java.math.BigDecimal;

/** One instrument's realised total, from pf_realised. */
public class SymbolRealised {

    private String symbol;
    private BigDecimal realised;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public BigDecimal getRealised() { return realised; }
    public void setRealised(BigDecimal realised) { this.realised = realised; }
}
