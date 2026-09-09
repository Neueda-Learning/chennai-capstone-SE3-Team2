package com.yellow.trade.dto;

import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

// contract: executedPrice is nullable, stays null until FILLED - not every order has one
public class OrderHistoryEntry {

    private final String orderId;
    private final Long accountId;
    private final String symbol;
    private final OrderSide side;
    private final int quantity;
    private final BigDecimal price;
    private final BigDecimal executedPrice;
    private final OrderStatus status;
    private final String idempotencyKey;
    private final Instant createdOn;

    public OrderHistoryEntry(String orderId, Long accountId, String symbol, OrderSide side,
                              int quantity, BigDecimal price, BigDecimal executedPrice,
                              OrderStatus status, String idempotencyKey, Instant createdOn) {
        this.orderId = orderId;
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.executedPrice = executedPrice;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.createdOn = createdOn;
    }

    public String getOrderId() { return orderId; }
    public Long getAccountId() { return accountId; }
    public String getSymbol() { return symbol; }
    public OrderSide getSide() { return side; }
    public int getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getExecutedPrice() { return executedPrice; }
    public OrderStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedOn() { return createdOn; }
}