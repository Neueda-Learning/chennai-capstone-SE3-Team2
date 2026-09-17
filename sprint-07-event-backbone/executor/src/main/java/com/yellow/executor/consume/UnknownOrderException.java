package com.yellow.executor.consume;

import java.util.UUID;

/**
 * The message names an order that is not in Postgres.
 *
 * <p>A POISON MESSAGE, not a transient failure, and the distinction is the
 * assessed part of story 613. No number of retries puts a row in a table, so
 * retrying this one blocks the partition it arrived on -- and a blocked
 * partition stops every account keyed to it, which with three partitions is
 * roughly a third of the customers. It is dead-lettered on the first attempt.
 *
 * <p>Contrast with a Fauxnance outage, which looks similar from the outside and
 * is handled in the opposite way: that one resolves the order as rejected,
 * because the order exists and deserves an answer.
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
