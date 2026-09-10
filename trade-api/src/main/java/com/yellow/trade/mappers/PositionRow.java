package com.yellow.trade.mappers;

import java.math.BigDecimal;

/**
 * One row of position, joined to its instrument's symbol.
 *
 * positionType is part of the natural key, not decoration: the same client
 * holding the same scrip intraday and as delivery is two rows, because one
 * is squared off tonight and one sits in the demat account.
 */
public class PositionRow {

    private Long positionId;
    private Long clientId;
    private Long instrumentId;
    private String symbol;
    private String positionType;
    private BigDecimal quantity;
    private BigDecimal averagePrice;

    public Long getPositionId() { return positionId; }
    public void setPositionId(Long positionId) { this.positionId = positionId; }

    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }

    public Long getInstrumentId() { return instrumentId; }
    public void setInstrumentId(Long instrumentId) { this.instrumentId = instrumentId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    /** INTRADAY or DELIVERY. */
    public String getPositionType() { return positionType; }
    public void setPositionType(String positionType) { this.positionType = positionType; }

    /** Fractional: mutual fund units are not whole numbers. */
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getAveragePrice() { return averagePrice; }
    public void setAveragePrice(BigDecimal averagePrice) { this.averagePrice = averagePrice; }
}
