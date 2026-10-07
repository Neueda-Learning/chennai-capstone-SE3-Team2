package com.yellow.trade.portfolio;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One holding priced (contracts/portfolio-api.yaml). An instrument that could
 * not be priced has no price, value or unrealised figure, and is stale.
 *
 * @param quantity a number, not the contract's integer: a fund holds fractional units
 */
public record PricedPosition(long accountId, String symbol, BigDecimal quantity, BigDecimal averageCost,
                             BigDecimal costBasis, BigDecimal lastPrice, BigDecimal marketValue,
                             BigDecimal unrealisedPnl, BigDecimal unrealisedPnlPercent, String currency,
                             Instant priceAsOf, boolean stale) {
}
