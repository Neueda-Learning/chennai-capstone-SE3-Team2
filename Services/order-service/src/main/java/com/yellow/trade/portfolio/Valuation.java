package com.yellow.trade.portfolio;

import com.yellow.trade.PlatformConstants;
import com.yellow.trade.mappers.PositionRow;
import com.yellow.trade.marketdata.PriceQuote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Holdings priced, by the contract's definitions:
 * cost basis = quantity * average cost; market value = quantity * last price;
 * unrealised = market value - cost basis, as a percentage of cost basis.
 *
 * Totals hold what was priced: a holding with no price is left out of them
 * rather than counted at zero, and the valuation says it is partial. Money is
 * shown to the paisa; nothing is rounded before it is summed.
 */
record Valuation(List<PricedPosition> positions, BigDecimal costBasis, BigDecimal marketValue,
                 BigDecimal unrealisedPnl, BigDecimal unrealisedPnlPercent, int pricedCount, boolean partial) {

    private static final int MONEY = 2;

    static Valuation of(long accountId, List<PositionRow> held, Map<String, PriceQuote> prices) {
        List<PricedPosition> positions = new ArrayList<>();
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal value = BigDecimal.ZERO;
        int priced = 0;
        for (PositionRow row : held) {
            BigDecimal positionCost = row.getQuantity().multiply(row.getAveragePrice());
            PriceQuote quote = prices.get(row.getSymbol());
            if (quote == null || quote.price() == null) {
                positions.add(new PricedPosition(accountId, row.getSymbol(), row.getQuantity(), row.getAveragePrice(),
                        money(positionCost), null, null, null, null, PlatformConstants.QUOTE_CURRENCY, null, true));
                continue;
            }
            BigDecimal positionValue = row.getQuantity().multiply(quote.price());
            BigDecimal unrealised = positionValue.subtract(positionCost);
            positions.add(new PricedPosition(accountId, row.getSymbol(), row.getQuantity(), row.getAveragePrice(),
                    money(positionCost), quote.price(), money(positionValue), money(unrealised),
                    percent(unrealised, positionCost), PlatformConstants.QUOTE_CURRENCY, quote.asOf(), quote.stale()));
            cost = cost.add(positionCost);
            value = value.add(positionValue);
            priced++;
        }
        BigDecimal unrealised = value.subtract(cost);
        return new Valuation(positions, money(cost), money(value), money(unrealised), percent(unrealised, cost),
                priced, priced < held.size());
    }

    /** Held, and not one of them could be priced: the contract's MKT-503. */
    boolean nothingPriced() {
        return !positions.isEmpty() && pricedCount == 0;
    }

    static BigDecimal money(BigDecimal amount) {
        return amount.setScale(MONEY, RoundingMode.HALF_UP);
    }

    /** Percentage points against the cost; null when there is no cost to measure against. */
    private static BigDecimal percent(BigDecimal unrealised, BigDecimal cost) {
        if (cost.signum() == 0) {
            return null;
        }
        return unrealised.multiply(BigDecimal.valueOf(100)).divide(cost, MONEY, RoundingMode.HALF_UP);
    }
}
