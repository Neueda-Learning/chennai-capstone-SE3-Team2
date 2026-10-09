package com.yellow.trade.portfolio;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One account's portfolio (Contracts/API Schemas/portfolio-api.yaml). With partial true,
 * the market value, cost basis and unrealised figures cover the holdings that
 * could be priced, so unrealised is still market value less cost basis.
 */
public record PortfolioSummary(long accountId, String baseCurrency, BigDecimal cashBalance, BigDecimal marketValue,
                               BigDecimal costBasis, BigDecimal unrealisedPnl, BigDecimal unrealisedPnlPercent,
                               BigDecimal realisedPnl, BigDecimal totalValue, int positionCount, boolean partial,
                               Instant asOf) {
}
