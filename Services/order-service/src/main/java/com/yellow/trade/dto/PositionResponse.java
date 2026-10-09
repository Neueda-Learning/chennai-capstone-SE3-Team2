package com.yellow.trade.dto;

import java.math.BigDecimal;

public record PositionResponse(
        Long accountId,
        String symbol,
        BigDecimal quantity,
        BigDecimal averageCost) {
}
