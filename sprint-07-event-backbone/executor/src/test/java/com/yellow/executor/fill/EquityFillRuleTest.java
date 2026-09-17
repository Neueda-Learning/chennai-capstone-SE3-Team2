package com.yellow.executor.fill;

import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;
import com.yellow.executor.quotes.Quote;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

/**
 * The fill rule at its boundaries.
 *
 * <p>Most of these tests sit on the exact edge, because that is where a fill
 * rule is either right or quietly wrong. An order a rupee inside the spread and
 * an order a rupee outside it are the same test; an order exactly at the ask is
 * the one that decides whether the comparison is {@code >} or {@code >=}, and
 * getting it wrong rejects business that should have traded.
 *
 * <p>Prices are MRF.NS-shaped on purpose -- around 125,000 with a three basis
 * point spread -- because a rule that works on a 200-rupee stock and breaks on
 * a six-figure one has a rounding bug nobody has found yet.
 */
class EquityFillRuleTest {

    private final FillRule rule = new EquityFillRule();

    // A real MRF.NS quote: bid < price < ask, spread 3bps.
    private static final BigDecimal BID = new BigDecimal("125083.94");
    private static final BigDecimal PRICE = new BigDecimal("125102.70693446");
    private static final BigDecimal ASK = new BigDecimal("125121.48");

    @Nested
    @DisplayName("a BUY settles at the ask, because that is where sellers are")
    class Buys {

        @Test
        @DisplayName("a limit above the ask fills at the ask, not at the limit")
        void limitAboveAskFillsAtAsk() {
            FillDecision decision = rule.decide(buyAt("125200.00"), quote());

            assertThat(decision, is(instanceOf(FillDecision.Fill.class)));
            // The customer was willing to pay 125,200 and pays 125,121.48.
            // Filling at the limit would pocket the difference.
            assertThat(((FillDecision.Fill) decision).executedPrice(),
                    comparesEqualTo(new BigDecimal("125121.4800")));
        }

        @Test
        @DisplayName("a limit exactly equal to the ask fills: the comparison is inclusive")
        void limitExactlyAtAskFills() {
            FillDecision decision = rule.decide(buyAt("125121.48"), quote());

            assertThat(decision, is(instanceOf(FillDecision.Fill.class)));
            assertThat(((FillDecision.Fill) decision).executedPrice(),
                    comparesEqualTo(new BigDecimal("125121.4800")));
        }

        @Test
        @DisplayName("a limit one tick below the ask is PRICE_NOT_MET")
        void limitOneTickBelowAskIsRejected() {
            FillDecision decision = rule.decide(buyAt("125121.4799"), quote());

            assertThat(decision, is(new FillDecision.Reject(RejectReason.PRICE_NOT_MET)));
        }

        @Test
        @DisplayName("a limit at the last traded price does not fill: nobody transacts at price")
        void limitAtLastTradedPriceIsRejected() {
            // The trap this rule exists to avoid. 125,102.71 is a perfectly
            // real number on the quote -- it is just not one a buyer can
            // transact at, because the cheapest seller wants 125,121.48.
            FillDecision decision = rule.decide(buyAt("125102.71"), quote());

            assertThat(decision, is(new FillDecision.Reject(RejectReason.PRICE_NOT_MET)));
        }

        @Test
        @DisplayName("a limit at the bid does not fill either")
        void limitAtBidIsRejected() {
            assertThat(rule.decide(buyAt("125083.94"), quote()),
                    is(new FillDecision.Reject(RejectReason.PRICE_NOT_MET)));
        }
    }

    @Nested
    @DisplayName("a SELL settles at the bid, because that is where buyers are")
    class Sells {

        @Test
        @DisplayName("a limit below the bid fills at the bid, not at the limit")
        void limitBelowBidFillsAtBid() {
            FillDecision decision = rule.decide(sellAt("125000.00"), quote());

            assertThat(decision, is(instanceOf(FillDecision.Fill.class)));
            assertThat(((FillDecision.Fill) decision).executedPrice(),
                    comparesEqualTo(new BigDecimal("125083.9400")));
        }

        @Test
        @DisplayName("a limit exactly equal to the bid fills: the comparison is inclusive")
        void limitExactlyAtBidFills() {
            FillDecision decision = rule.decide(sellAt("125083.94"), quote());

            assertThat(decision, is(instanceOf(FillDecision.Fill.class)));
            assertThat(((FillDecision.Fill) decision).executedPrice(),
                    comparesEqualTo(new BigDecimal("125083.9400")));
        }

        @Test
        @DisplayName("a limit one tick above the bid is PRICE_NOT_MET")
        void limitOneTickAboveBidIsRejected() {
            assertThat(rule.decide(sellAt("125083.9401"), quote()),
                    is(new FillDecision.Reject(RejectReason.PRICE_NOT_MET)));
        }

        @Test
        @DisplayName("a limit at the last traded price does not fill")
        void limitAtLastTradedPriceIsRejected() {
            assertThat(rule.decide(sellAt("125102.71"), quote()),
                    is(new FillDecision.Reject(RejectReason.PRICE_NOT_MET)));
        }
    }

    @Nested
    @DisplayName("the price compared is the price stored")
    class Rounding {

        @Test
        @DisplayName("an ask with more decimals than the column holds is rounded before the comparison")
        void askIsRoundedBeforeComparing() {
            // Ask 125121.48449 rounds to 125121.4845 at four places. A limit of
            // exactly that fills, and fills at exactly that: if the comparison
            // ran at full precision the limit would be below the ask and this
            // would reject, while the stored price would still have rounded to
            // the limit -- an order rejected at a price it actually matched.
            Quote awkward = quoteWith(BID, new BigDecimal("125121.48449"));

            FillDecision decision = rule.decide(buyAt("125121.4845"), awkward);

            assertThat(decision, is(instanceOf(FillDecision.Fill.class)));
            assertThat(((FillDecision.Fill) decision).executedPrice(),
                    comparesEqualTo(new BigDecimal("125121.4845")));
        }

        @Test
        @DisplayName("the stored price always carries the column's scale")
        void storedPriceCarriesColumnScale() {
            FillDecision decision = rule.decide(buyAt("200.00"), quoteWith(
                    new BigDecimal("99.5"), new BigDecimal("100.5")));

            assertThat(((FillDecision.Fill) decision).executedPrice().scale(),
                    is(ExecutionPrice.SCALE));
        }

        @Test
        @DisplayName("a half-paisa on the boundary rounds up, and then fills")
        void halfRoundsUp() {
            // 100.50005 -> 100.5001 at HALF_UP. A limit of 100.5000 is then
            // below the ask and must not fill.
            Quote awkward = quoteWith(new BigDecimal("99.5"), new BigDecimal("100.50005"));

            assertThat(rule.decide(buyAt("100.5000"), awkward),
                    is(new FillDecision.Reject(RejectReason.PRICE_NOT_MET)));
            assertThat(rule.decide(buyAt("100.5001"), awkward),
                    is(instanceOf(FillDecision.Fill.class)));
        }
    }

    @Test
    @DisplayName("a mutual fund is not priceable, and says so permanently")
    void mutualFundIsNotPriceable() {
        FillDecision decision = new MutualFundFillRule().decide(buyAt("100.00"), quote());

        assertThat(decision, is(new FillDecision.Reject(RejectReason.INSTRUMENT_NOT_PRICEABLE)));
        // Permanent, not transient. A consumer must be able to tell this from
        // a Fauxnance outage: one is worth a manual retry and one never is.
        assertThat(RejectReason.INSTRUMENT_NOT_PRICEABLE.isPermanent(), is(true));
        assertThat(RejectReason.NO_PRICE.isPermanent(), is(false));
    }

    // ------------------------------------------------------------ fixtures

    private static OrderSnapshot buyAt(String limit) {
        return order(OrderSide.BUY, limit);
    }

    private static OrderSnapshot sellAt(String limit) {
        return order(OrderSide.SELL, limit);
    }

    private static OrderSnapshot order(OrderSide side, String limit) {
        return new OrderSnapshot(
                UUID.randomUUID(), 3L, 1L, "MRF.NS", side,
                new BigDecimal("4"), new BigDecimal(limit), OrderStatus.NEW);
    }

    private static Quote quote() {
        return quoteWith(BID, ASK);
    }

    private static Quote quoteWith(BigDecimal bid, BigDecimal ask) {
        return new Quote("MRF.NS", PRICE, bid, ask, new BigDecimal("3"), "INR",
                new BigDecimal("92.70693446"), new BigDecimal("0.0741596147988161"),
                new BigDecimal("125010"), Instant.parse("2026-09-17T05:05:00Z"),
                "unknown", false, "synthetic");
    }
}
