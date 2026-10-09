package com.yellow.exceptions;

import java.util.UUID;

public class DuplicateOrderException extends TradeException {

    private final String idempotencyKey;
    private final UUID existingOrderId;

    public DuplicateOrderException(String idempotencyKey, UUID existingOrderId) {
        super("ORD-409", "Duplicate order");
        this.idempotencyKey = idempotencyKey;
        this.existingOrderId = existingOrderId;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    /** Null when the duplicate was caught by the pre-check, which reads no id. */
    public UUID existingOrderId() {
        return existingOrderId;
    }
}