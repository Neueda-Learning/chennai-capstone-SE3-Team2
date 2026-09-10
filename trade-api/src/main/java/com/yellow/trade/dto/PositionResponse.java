package com.yellow.trade.dto;

import java.math.BigDecimal;

/**
 * contracts/trade-api.yaml -> PositionResponse.
 *
 * quantity is decimal rather than the contract's int32. That is the one
 * deviation in this service and it is recorded in contracts/DEVIATIONS.md:
 * a mutual fund allotment is money divided by that day's NAV, our schema
 * types it NUMERIC(18,6) for that reason, and the seeded data holds real
 * fractional positions. Reading 152.386000 into an int throws; rounding it
 * reports a holding the client does not have.
 *
 * averageCost is the weighted average cost basis. A buy recalculates it; a
 * sell reduces the quantity and leaves it alone, which is what makes realised
 * profit and loss computable at the point of sale.
 */
public record PositionResponse(
        Long accountId,
        String symbol,
        BigDecimal quantity,
        BigDecimal averageCost) {
}
