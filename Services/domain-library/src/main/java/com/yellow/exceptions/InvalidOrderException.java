package com.yellow.exceptions;

/** A quantity or a price outside its permitted range. */
public class InvalidOrderException extends TradeException {

    private final String field;
    private final String submittedValue;

    public InvalidOrderException(String field, String submittedValue) {
        super("VAL-422", "Invalid input");
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
