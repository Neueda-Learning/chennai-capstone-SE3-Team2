package com.yellow.trade.dto;

import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;

import java.math.BigDecimal;

public class OrderResponse {

    private final String orderId;
    private final OrderStatus status;
    private final String message;
    private final String symbol;
    private final OrderSide side;
    private final int quantity;
    private final BigDecimal price;

    public OrderResponse(String orderId, OrderStatus status, String message, String symbol,
                          OrderSide side, int quantity, BigDecimal price) {
        this.orderId = orderId;
        this.status = status;
        this.message = message;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
    }

    public String getOrderId() { return orderId; }
    public OrderStatus getStatus() { return status; }
    public String getMessage() { return message; }
    public String getSymbol() { return symbol; }
    public OrderSide getSide() { return side; }
    public int getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }
}