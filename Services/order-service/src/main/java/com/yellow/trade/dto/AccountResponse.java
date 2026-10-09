package com.yellow.trade.dto;

import com.yellow.enums.AccountStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
        Long id,
        String accountId,
        String holderName,
        BigDecimal cashBalance,
        AccountStatus status,
        int version,
        Instant lastUpdated) {
}
