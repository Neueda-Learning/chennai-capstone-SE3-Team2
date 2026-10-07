package com.yellow.trade.portfolio;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Profit and loss (contracts/portfolio-api.yaml). from and to bound the
 * realised figure only, as dates in IST; unrealised is always as at now.
 *
 * @param bySymbol present only when it was asked for
 */
public record PnlResponse(long accountId, String baseCurrency, LocalDate from, LocalDate to, BigDecimal realisedPnl,
                          BigDecimal unrealisedPnl, BigDecimal totalPnl,
                          @JsonInclude(JsonInclude.Include.NON_NULL) List<SymbolPnl> bySymbol, Instant asOf) {
}
