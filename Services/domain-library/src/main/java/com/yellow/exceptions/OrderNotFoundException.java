package com.yellow.exceptions;

import java.util.UUID;

public class OrderNotFoundException extends TradeException {

    private final UUID requestedOrderId;

    public OrderNotFoundException(UUID requestedOrderId) {
        super("ORD-409", "Order not found");
        this.requestedOrderId = requestedOrderId;
    }

    public UUID requestedOrderId() {
        return requestedOrderId;
    }
}
