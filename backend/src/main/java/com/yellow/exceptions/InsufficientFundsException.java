package com.yellow.exceptions;

import java.math.BigDecimal;

public class InsufficientFundsException extends TradeException {

    private final BigDecimal required;
    private final BigDecimal available;

    public InsufficientFundsException(BigDecimal required, BigDecimal available) {
        super("ORD-400", "Insufficient funds");
        this.required = required;
        this.available = available;
    }

    public BigDecimal required() {
        return required;
    }

    public BigDecimal available() {
        return available;
    }

    public InsufficientFundsException() {
        this(null, null);
    }
}
