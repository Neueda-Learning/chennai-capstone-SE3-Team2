package com.yellow.executor.consume;

/**
 * The message is not one this consumer can act on: an event type it does not
 * recognise, a missing order identifier, an identifier that is not a UUID.
 *
 * <p>All of these are poison. They will never succeed, so story 613
 * dead-letters them on the first attempt rather than retrying -- one bad
 * message retried forever blocks its partition, and a blocked partition stops
 * every account keyed to it.
 */
public class UnexpectedEventTypeException extends RuntimeException {

    public UnexpectedEventTypeException(String detail) {
        super("unprocessable message: " + detail);
    }
}
