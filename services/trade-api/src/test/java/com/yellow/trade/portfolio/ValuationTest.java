package com.yellow.trade.portfolio;

import com.yellow.trade.mappers.PositionRow;
import com.yellow.trade.marketdata.PriceQuote;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/** contracts/portfolio-api.yaml, "Definitions": the arithmetic the module is assessed on. */
class ValuationTest {

    private static final Instant AS_OF = Instant.parse("2026-10-07T04:00:00Z");

    private static PositionRow held(String symbol, String quantity, String averagePrice) {
        PositionRow row = new PositionRow();
        row.setClientId(3L);
        row.setSymbol(symbol);
        row.setQuantity(new BigDecimal(quantity));
        row.setAveragePrice(new BigDecimal(averagePrice));
        return row;
    }

    private static PriceQuote priced(String symbol, String price, boolean stale) {
        return new PriceQuote(symbol, new BigDecimal(price), null, null, null, null, null, "INR", AS_OF, stale);
    }

    @Test
    @DisplayName("cost basis is quantity times average cost; market value, quantity times last price; unrealised, the difference")
    void definitions() {
        Valuation valuation = Valuation.of(3L, List.of(held("ITC.NS", "200", "228.40")),
                Map.of("ITC.NS", priced("ITC.NS", "232.71", false)));

        PricedPosition position = valuation.positions().get(0);
        assertThat(position.costBasis(), comparesEqualTo(new BigDecimal("45680.00")));
        assertThat(position.marketValue(), comparesEqualTo(new BigDecimal("46542.00")));
        assertThat(position.unrealisedPnl(), comparesEqualTo(new BigDecimal("862.00")));
        assertThat(position.unrealisedPnlPercent(), comparesEqualTo(new BigDecimal("1.89")));
        assertThat(position.priceAsOf(), is(AS_OF));
        assertThat(position.stale(), is(false));
        assertThat(position.currency(), is("INR"));
    }

    @Test
    @DisplayName("the totals are the sums of what was priced")
    void totals() {
        Valuation valuation = Valuation.of(3L, List.of(held("ITC.NS", "200", "228.40"), held("SBIN.NS", "10", "800")),
                Map.of("ITC.NS", priced("ITC.NS", "232.71", false), "SBIN.NS", priced("SBIN.NS", "780", false)));

        assertThat(valuation.costBasis(), comparesEqualTo(new BigDecimal("53680.00")));
        assertThat(valuation.marketValue(), comparesEqualTo(new BigDecimal("54342.00")));
        assertThat(valuation.unrealisedPnl(), comparesEqualTo(new BigDecimal("662.00")));
        assertThat(valuation.partial(), is(false));
        assertThat(valuation.pricedCount(), is(2));
    }

    @Test
    @DisplayName("a holding that could not be priced keeps its cost, has no value, is stale, and makes the answer partial")
    void unpriced() {
        Valuation valuation = Valuation.of(3L, List.of(held("ITC.NS", "200", "228.40"), held("INFY.NS", "40", "1580.25")),
                Map.of("ITC.NS", priced("ITC.NS", "232.71", false)));

        PricedPosition infy = valuation.positions().get(1);
        assertThat(infy.costBasis(), comparesEqualTo(new BigDecimal("63210.00")));
        assertThat(infy.lastPrice(), is(nullValue()));
        assertThat(infy.marketValue(), is(nullValue()));
        assertThat(infy.unrealisedPnl(), is(nullValue()));
        assertThat(infy.unrealisedPnlPercent(), is(nullValue()));
        assertThat(infy.priceAsOf(), is(nullValue()));
        assertThat(infy.stale(), is(true));
        assertThat(valuation.partial(), is(true));
        // The totals hold only what was priced: a missing price is not a price of zero.
        assertThat(valuation.marketValue(), comparesEqualTo(new BigDecimal("46542.00")));
        assertThat(valuation.costBasis(), comparesEqualTo(new BigDecimal("45680.00")));
    }

    @Test
    @DisplayName("a stale price is used, and marked: stale is a state, not an error")
    void stale() {
        Valuation valuation = Valuation.of(3L, List.of(held("ITC.NS", "1", "200")),
                Map.of("ITC.NS", priced("ITC.NS", "250", true)));

        assertThat(valuation.positions().get(0).marketValue(), comparesEqualTo(new BigDecimal("250.00")));
        assertThat(valuation.positions().get(0).stale(), is(true));
        assertThat(valuation.partial(), is(false));
    }

    @Test
    @DisplayName("a fund's fractional units are valued as they are held")
    void fractionalUnits() {
        Valuation valuation = Valuation.of(3L, List.of(held("122639", "12.345678", "80.1234")),
                Map.of("122639", priced("122639", "88.7620", false)));

        PricedPosition fund = valuation.positions().get(0);
        assertThat(fund.quantity(), comparesEqualTo(new BigDecimal("12.345678")));
        assertThat(fund.costBasis(), comparesEqualTo(new BigDecimal("989.18")));
        assertThat(fund.marketValue(), comparesEqualTo(new BigDecimal("1095.83")));
    }

    @Test
    @DisplayName("the percentage is against cost basis, and null when there is no cost to measure against")
    void percentage() {
        Valuation valuation = Valuation.of(3L, List.of(held("GIFT.NS", "5", "0")),
                Map.of("GIFT.NS", priced("GIFT.NS", "10", false)));

        assertThat(valuation.positions().get(0).unrealisedPnlPercent(), is(nullValue()));
        assertThat(valuation.unrealisedPnlPercent(), is(nullValue()));
    }

    @Test
    @DisplayName("nothing held: zero everywhere, nothing partial, and nothing to price")
    void empty() {
        Valuation valuation = Valuation.of(3L, List.of(), Map.of());

        assertThat(valuation.marketValue(), comparesEqualTo(BigDecimal.ZERO));
        assertThat(valuation.partial(), is(false));
        assertThat(valuation.positions().isEmpty(), is(true));
    }
}
