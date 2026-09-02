package com.yellow.exceptions;

public class DuplicateOrderException extends TradeException {

    public DuplicateOrderException() {
        super("ORD-409", "Duplicate order");
    }
}