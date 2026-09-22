package com.yellow.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Holdings position for an instrument")
public record PositionResponse(
        @Schema(description = "Account ID", example = "1")
        Long accountId,
        @Schema(description = "Instrument symbol", example = "AAPL")
        String symbol,
        @Schema(description = "Quantity held", example = "100")
        BigDecimal quantity,
        @Schema(description = "Average cost per unit", example = "150.50")
        BigDecimal averageCost) {
}
