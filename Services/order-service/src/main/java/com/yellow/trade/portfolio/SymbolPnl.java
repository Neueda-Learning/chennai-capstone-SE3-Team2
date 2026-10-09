package com.yellow.trade.portfolio;

import java.math.BigDecimal;

/** One instrument's profit and loss; unrealised is zero for one not held, or not priced. */
public record SymbolPnl(String symbol, BigDecimal realisedPnl, BigDecimal unrealisedPnl, BigDecimal totalPnl) {
}
