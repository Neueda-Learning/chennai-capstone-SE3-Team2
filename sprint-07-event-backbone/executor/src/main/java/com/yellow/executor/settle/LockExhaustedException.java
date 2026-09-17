package com.yellow.executor.settle;

/**
 * Thrown when the optimistic lock on {@code client_account.version} has been
 * lost more times than the retry budget allows.
 *
 * <p>Unchecked so that {@link FullSettlement}'s {@code @Transactional} method
 * rolls back on it automatically, leaving the order at {@code NEW} and the
 * account unchanged. The consumer propagates this upwards without acknowledging,
 * so the message is redelivered and tried again later.
 */
public class LockExhaustedException extends RuntimeException {

    public LockExhaustedException(String message) {
        super(message);
    }
}
