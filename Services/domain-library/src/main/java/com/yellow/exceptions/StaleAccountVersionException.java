package com.yellow.exceptions;

/** The account row changed between being read and being written. */
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
