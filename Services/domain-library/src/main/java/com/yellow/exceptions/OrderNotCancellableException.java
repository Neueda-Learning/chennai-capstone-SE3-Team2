package com.yellow.exceptions;

import com.yellow.enums.OrderStatus;

public class OrderNotCancellableException extends TradeException {

    private final OrderStatus actualStatus;

    public OrderNotCancellableException(OrderStatus actualStatus) {
        super("ORD-409", "Order is not cancellable");
        this.actualStatus = actualStatus;
    }

    public OrderStatus actualStatus() {
        return actualStatus;
    }
}