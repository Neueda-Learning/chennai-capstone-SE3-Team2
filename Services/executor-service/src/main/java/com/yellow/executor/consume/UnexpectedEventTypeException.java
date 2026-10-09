package com.yellow.executor.consume;

/**
 * The message is not one this consumer can act on: an event type it does not
 * recognise, a missing order identifier, an identifier that is not a UUID.
 */
public class UnexpectedEventTypeException extends RuntimeException {

    public UnexpectedEventTypeException(String detail) {
        super("unprocessable message: " + detail);
    }
}
