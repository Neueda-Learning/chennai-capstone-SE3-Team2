package com.yellow.executor.consume;

import java.util.UUID;

/**
 * The message names an order that is not in Postgres. A POISON MESSAGE, not a
 * transient failure, and the distinction is the assessed part of story 613.
 */
public class UnknownOrderException extends RuntimeException {

    private final UUID orderId;

    public UnknownOrderException(UUID orderId) {
        super("no order " + orderId + " in Postgres");
        this.orderId = orderId;
    }

    public UnknownOrderException(UUID orderId, String message) {
        super(message);
        this.orderId = orderId;
    }

    public UUID orderId() {
        return orderId;
    }
}
