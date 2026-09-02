package com.yellow.exceptions;

public class InvalidOrderException extends TradeException {

    public InvalidOrderException() {
        super("ORD-422", "Invalid Order");
    }
}