package com.yellow.trade.dto;

import java.math.BigDecimal;

/**
 * contracts/trade-api.yaml -> PositionResponse.
 *
 * quantity is decimal, not an int: mutual fund allotments are fractional, and
 * a holding of 240.117 units is an ordinary row in this schema.
 *
 * positionType is carried because it is part of the natural key. Without it
 * the same scrip held intraday and as delivery arrives as two entries a
 * client cannot tell apart.
 */
public record PositionResponse(
        Long accountId,
        String symbol,
        String positionType,
        BigDecimal quantity,
        BigDecimal averagePrice) {
}
