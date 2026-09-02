package com.yellow.exceptions;

public class InsufficientFundsException extends TradeException {

    public InsufficientFundsException() {
        super("ORD-400", "Insufficient funds");
    }
}