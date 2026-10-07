package com.yellow.trade.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One row of pf_realised: one sale, booked. */
public class RealisedRow {

    private UUID eventId;
    private UUID orderId;
    private Long clientId;
    private Long instrumentId;
    private String symbol;
    private BigDecimal quantity;
    private BigDecimal salePrice;
    private BigDecimal averageCost;
    private BigDecimal realised;
    private Instant bookedAt;

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }
    public Long getInstrumentId() { return instrumentId; }
    public void setInstrumentId(Long instrumentId) { this.instrumentId = instrumentId; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getSalePrice() { return salePrice; }
    public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice; }
    public BigDecimal getAverageCost() { return averageCost; }
    public void setAverageCost(BigDecimal averageCost) { this.averageCost = averageCost; }
    public BigDecimal getRealised() { return realised; }
    public void setRealised(BigDecimal realised) { this.realised = realised; }
    public Instant getBookedAt() { return bookedAt; }
    public void setBookedAt(Instant bookedAt) { this.bookedAt = bookedAt; }
}
