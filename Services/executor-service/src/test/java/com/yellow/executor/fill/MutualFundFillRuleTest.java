package com.yellow.executor.fill;

import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;
import com.yellow.executor.quotes.Quote;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;

/**
 * A fund deals at its NAV: one price, the same both ways, published once a
 * day. The limit is honoured exactly as it is for a stock.
 */
class MutualFundFillRuleTest {

    private final FillRule rule = new MutualFundFillRule();

    /** Parag Parikh Flexi Cap, Direct Growth, as the MF NAV service reported it. */
    private static final BigDecimal NAV = new BigDecimal("88.2569");

    @Test
    @DisplayName("a BUY whose limit is at or above the NAV fills at the NAV")
    void buyAtOrAboveTheNavFillsAtTheNav() {
        assertThat(executedPrice(rule.decide(order(OrderSide.BUY, "90.00"), nav())), comparesEqualTo(NAV));
        assertThat(executedPrice(rule.decide(order(OrderSide.BUY, "88.2569"), nav())), comparesEqualTo(NAV));
    }

    @Test
    @DisplayName("a BUY whose limit is below the NAV is not met")
    void buyBelowTheNavIsNotMet() {
        assertThat(rule.decide(order(OrderSide.BUY, "88.25"), nav()),
                is(new FillDecision.Reject(RejectReason.PRICE_NOT_MET)));
    }

    @Test
    @DisplayName("a SELL whose limit is at or below the NAV fills at the NAV")
    void sellAtOrBelowTheNavFillsAtTheNav() {
        assertThat(executedPrice(rule.decide(order(OrderSide.SELL, "1.00"), nav())), comparesEqualTo(NAV));
        assertThat(executedPrice(rule.decide(order(OrderSide.SELL, "88.2569"), nav())), comparesEqualTo(NAV));
    }

    @Test
    @DisplayName("a SELL whose limit is above the NAV is not met")
    void sellAboveTheNavIsNotMet() {
        assertThat(rule.decide(order(OrderSide.SELL, "88.26"), nav()),
                is(new FillDecision.Reject(RejectReason.PRICE_NOT_MET)));
    }

    // ------------------------------------------------------------ fixtures

    private static BigDecimal executedPrice(FillDecision decision) {
        return ((FillDecision.Fill) decision).executedPrice();
    }

    private static OrderSnapshot order(OrderSide side, String limit) {
        return new OrderSnapshot(UUID.randomUUID(), 3L, 14L, "122639", side,
                new BigDecimal("10"), new BigDecimal(limit), OrderStatus.NEW);
    }

    private static Quote nav() {
        return Quote.ofNav("122639", NAV, Instant.parse("2026-10-04T05:00:00Z"), "2026-10-01", "cache");
    }
}
