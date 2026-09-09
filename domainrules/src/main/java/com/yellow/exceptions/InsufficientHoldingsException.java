package com.yellow.exceptions;

import java.math.BigDecimal;

public class InsufficientHoldingsException extends TradeException {

    private final BigDecimal requested;
    private final BigDecimal held;

    public InsufficientHoldingsException(BigDecimal requested, BigDecimal held) {
        super("ORD-409", "Insufficient holdings");
        this.requested = requested;
        this.held = held;
    }

    public BigDecimal requested() {
        return requested;
    }

    public BigDecimal held() {
        return held;
    }

}
