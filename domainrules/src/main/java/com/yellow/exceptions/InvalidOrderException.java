package com.yellow.exceptions;

/**
 * A quantity or a price outside its permitted range.
 *
 * VAL-422 is the catalogue's code for a request that failed field
 * validation, and this is the same failure reached by a different route:
 * rules 4 and 5 are checked in the domain as well as on the DTO, because
 * the domain has to hold for the Trade Executor replaying an order that
 * never ran a validator. One outcome, so one code.
 *
 * The offending field and value are typed fields for the server log. They
 * are deliberately not in the message, which becomes the response body.
 */
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
