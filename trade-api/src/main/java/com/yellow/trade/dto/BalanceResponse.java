package com.yellow.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Current cash balance for an account")
public record BalanceResponse(
        @Schema(description = "Account ID", example = "1")
        Long accountId,
        @Schema(description = "Available cash balance", example = "5000.00")
        BigDecimal cashBalance,
        @Schema(description = "Currency code", example = "USD")
        String currency,
        @Schema(description = "Timestamp when balance was calculated")
        Instant asOf) {
}
