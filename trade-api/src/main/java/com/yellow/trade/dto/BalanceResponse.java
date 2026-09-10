package com.yellow.trade.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * contracts/trade-api.yaml -> BalanceResponse. Cash only.
 *
 * availableFunds is returned rather than left to the client to compute:
 * it is the figure rule 6 tests a buy against, and a client subtracting
 * the wrong pair would disagree with the server about what it can afford.
 */
public record BalanceResponse(
        Long accountId,
        BigDecimal cashBalance,
        BigDecimal blockedFunds,
        BigDecimal availableFunds,
        String currency,
        Instant asOf) {
}
