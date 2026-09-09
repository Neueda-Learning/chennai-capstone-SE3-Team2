package com.yellow.trade.mappers;

import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class OrderHistoryRow {
    public UUID orderId;
    public Long accountId;
    public String symbol;
    public OrderSide side;
    public BigDecimal quantity;
    public BigDecimal price;
    public BigDecimal executedPrice;
    public OrderStatus status;
    public String idempotencyKey;
    public Instant createdOn;
}