package com.yellow.exceptions;

public class DuplicateOrderException extends TradeException {

    private final String idempotencyKey;
    private final Long existingOrderId;

    public DuplicateOrderException(String idempotencyKey, Long existingOrderId) {
        super("ORD-409", "Duplicate order");
        this.idempotencyKey = idempotencyKey;
        this.existingOrderId = existingOrderId;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    /** Null when the duplicate was caught by the pre-check, which reads no id. */
    public Long existingOrderId() {
        return existingOrderId;
    }

    public DuplicateOrderException() {
        this(null, null);
    }
}
