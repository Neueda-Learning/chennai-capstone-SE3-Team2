package com.yellow.trade.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * contracts/trade-api.yaml -> BalanceResponse.
 *
 * The contract says "available cash only", so cashBalance is
 * balance minus blocked_funds -- the figure business rule 6 tests a buy
 * against, so the client sees the same number the server judges by. Returning
 * the raw balance would let a client believe it can afford an order the server
 * then refuses.
 *
 * The envelope declares additionalProperties: false, so the blocked and
 * available figures are deliberately NOT returned alongside it. A client that
 * needs the split reads the account endpoint.
 *
 * No holdings value: portfolio valuation needs a live quote and belongs to the
 * Sprint 10 extension, contracted separately.
 */
public record BalanceResponse(
        Long accountId,
        BigDecimal cashBalance,
        String currency,
        Instant asOf) {
}
