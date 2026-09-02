package com.yellow.exceptions;

public class InsufficientHoldingsException extends TradeException {

    public InsufficientHoldingsException() {
        super("ORD-409", "Insufficient holdings");
    }
}