package com.yellow.exceptions;

/**
 * The account row changed between being read and being written.
 *
 * Raised when an optimistic-locked update affects zero rows: another writer
 * committed first, so the balance this order was judged against is stale and
 * the judgement cannot be trusted.
 *
 * It carries ORD-409 like a duplicate order does, because that is the code the
 * catalogue gives a conflict -- but it is a separate type on purpose. Reusing
 * DuplicateOrderException would put "Duplicate order" in the log for something
 * that is not one, and the next person to read that log would go looking for
 * an idempotency key that was never involved.
 *
 * Added beyond the six the Sprint 5 brief specifies, which it permits provided
 * the type descends from the same base. Sprint 7's executor takes the same
 * lock and needs the same meaning.
 */
public class StaleAccountVersionException extends TradeException {

    private final Long accountId;
    private final int expectedVersion;

    public StaleAccountVersionException(Long accountId, int expectedVersion) {
        super("ORD-409", "Order could not be placed, please try again");
        this.accountId = accountId;
        this.expectedVersion = expectedVersion;
    }

    public Long accountId() {
        return accountId;
    }

    /** The version the row was read at, which is no longer the version it holds. */
    public int expectedVersion() {
        return expectedVersion;
    }
}
