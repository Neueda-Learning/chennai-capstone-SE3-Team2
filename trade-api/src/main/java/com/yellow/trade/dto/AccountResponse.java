package com.yellow.trade.dto;

import com.yellow.enums.AccountStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * contracts/trade-api.yaml -> AccountResponse. Field for field.
 *
 * Two identifiers, and they are not interchangeable. `id` is the numeric
 * account key -- what every other endpoint in the contract calls `accountId`.
 * `accountId` here is the string business identifier a support call quotes,
 * and this is the ONE field in the whole platform where that name carries the
 * other meaning.
 *
 * `version` is exposed because the contract requires it: it is the optimistic
 * lock counter, and a client that holds it can tell whether the account has
 * moved since it last looked.
 */
public record AccountResponse(
        Long id,
        String accountId,
        String holderName,
        BigDecimal cashBalance,
        AccountStatus status,
        int version,
        Instant lastUpdated) {
}
