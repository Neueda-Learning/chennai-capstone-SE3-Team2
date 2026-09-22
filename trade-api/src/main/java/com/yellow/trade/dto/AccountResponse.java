package com.yellow.trade.dto;

import com.yellow.enums.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Account information including balance and status")
public record AccountResponse(
        @Schema(description = "Account internal identifier", example = "1")
        Long id,
        @Schema(description = "Account external identifier", example = "ACC-12345")
        String accountId,
        @Schema(description = "Name of account holder", example = "John Doe")
        String holderName,
        @Schema(description = "Current cash balance", example = "10000.50")
        BigDecimal cashBalance,
        @Schema(description = "Account status (ACTIVE, SUSPENDED, CLOSED)")
        AccountStatus status,
        @Schema(description = "Version for optimistic locking", example = "1")
        int version,
        @Schema(description = "Timestamp of last update")
        Instant lastUpdated) {
}
