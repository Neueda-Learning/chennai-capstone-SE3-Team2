package com.yellow.trade.mappers;

import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of orders, joined to its instrument's symbol.
 *
 * orderId is supplied by the application, not the database: the domain mints
 * it in Order.place() before the insert, so a retry can carry the identity it
 * was given. See migration 002_api_alignment.sql.
 */
public class OrderRow {

    private UUID orderId;
    private Long clientId;
    private Long instrumentId;
    private String symbol;
    private OrderSide side;
    private String orderType;
    private String productType;
    private BigDecimal price;
    private BigDecimal quantity;
    private BigDecimal fillPrice;
    private OrderStatus status;
    private String idempotencyKey;
    private Instant datePlaced;
    private Instant resolvedAt;

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }

    public Long getInstrumentId() { return instrumentId; }
    public void setInstrumentId(Long instrumentId) { this.instrumentId = instrumentId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public OrderSide getSide() { return side; }
    public void setSide(OrderSide side) { this.side = side; }

    /** MARKET or LIMIT. Defaults to MARKET; see migration 002. */
    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }

    /** MIS or CNC. Defaults to CNC; see migration 002. */
    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }

    /** What the customer submitted: a ceiling on a buy, a floor on a sell. */
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    /** What it actually filled at. Null until FILLED. */
    public BigDecimal getFillPrice() { return fillPrice; }
    public void setFillPrice(BigDecimal fillPrice) { this.fillPrice = fillPrice; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public Instant getDatePlaced() { return datePlaced; }
    public void setDatePlaced(Instant datePlaced) { this.datePlaced = datePlaced; }

    /** Null exactly when the status is NEW. */
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
}
