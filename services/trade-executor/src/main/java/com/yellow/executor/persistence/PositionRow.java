package com.yellow.executor.persistence;

import java.math.BigDecimal;

/** A holding. Absent rather than zero when the position is closed. */
public class PositionRow {

    private Long positionId;
    private Long clientId;
    private Long instrumentId;
    private BigDecimal quantity;
    private BigDecimal averagePrice;

    public Long getPositionId() { return positionId; }
    public void setPositionId(Long positionId) { this.positionId = positionId; }

    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }

    public Long getInstrumentId() { return instrumentId; }
    public void setInstrumentId(Long instrumentId) { this.instrumentId = instrumentId; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getAveragePrice() { return averagePrice; }
    public void setAveragePrice(BigDecimal averagePrice) { this.averagePrice = averagePrice; }
}
