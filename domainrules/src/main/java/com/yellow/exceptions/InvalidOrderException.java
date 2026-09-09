package com.yellow.exceptions;

public class InvalidOrderException extends TradeException {

    private final String field;
    private final String submittedValue;

    public InvalidOrderException(String field, String submittedValue) {
        super("ORD-422", "Invalid Order");
        this.field = field;
        this.submittedValue = submittedValue;
    }

    public String field() {
        return field;
    }

    public String submittedValue() {
        return submittedValue;
    }

}
