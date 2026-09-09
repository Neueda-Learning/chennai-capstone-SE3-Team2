package com.yellow.trade.dto;

import java.math.BigDecimal;

// contract: quantity is int32, domain Position.quantity() is BigDecimal -
// the conversion happens where we build this DTO, not in this class.
public class PositionResponse {

    private final Long accountId;
    private final String symbol;
    private final int quantity;
    private final BigDecimal averageCost;

    public PositionResponse(Long accountId, String symbol, int quantity, BigDecimal averageCost) {
        this.accountId = accountId;
        this.symbol = symbol;
        this.quantity = quantity;
        this.averageCost = averageCost;
    }

    public Long getAccountId() { return accountId; }
    public String getSymbol() { return symbol; }
    public int getQuantity() { return quantity; }
    public BigDecimal getAverageCost() { return averageCost; }
}