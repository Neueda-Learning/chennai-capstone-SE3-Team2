package com.yellow.trade.mappers;

import java.math.BigDecimal;

public class PositionRow {
    public Long accountId;
    public String symbol;
    public BigDecimal quantity;
    public BigDecimal averageCost;
}