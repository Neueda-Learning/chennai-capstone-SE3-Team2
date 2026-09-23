package com.yellow.executor.checks;

import com.yellow.entities.Account;
import com.yellow.entities.Instrument;
import com.yellow.entities.Position;
import com.yellow.enums.AccountStatus;
import com.yellow.enums.AssetClass;
import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;
import com.yellow.executor.fill.OrderSnapshot;
import com.yellow.executor.fill.RejectReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * The checks around the fill rule. The re-check tests all describe the same
 * shape of event: something was true when the order was accepted and is not
 * true now.
 */
class PreTradeChecksTest {

    @Nested
    @DisplayName("before a quote is fetched, because these cost no quota")
    class BeforePricing {

        @Test
        @DisplayName("an instrument delisted after acceptance is rejected without spending a request")
        void delistedInstrumentIsRejected() {
            assertThat(PreTradeChecks.beforePricing(instrument(AssetClass.EQUITY, false), active()),
                    is(Optional.of(RejectReason.INSTRUMENT_NOT_TRADABLE)));
        }

        @Test
        @DisplayName("a mutual fund is not priceable, and no quote is worth asking for")
        void mutualFundIsNotPriceable() {
            // Fauxnance's registry is closed at equity, etf, fx and crypto,
            // so this would 404. More to the point, a fund has no bid or ask
            assertThat(PreTradeChecks.beforePricing(instrument(AssetClass.MUTUAL_FUND, true), active()),
                    is(Optional.of(RejectReason.INSTRUMENT_NOT_PRICEABLE)));
        }

        @Test
        @DisplayName("an account suspended after acceptance does not trade")
        void suspendedAccountDoesNotTrade() {
            // The order was legitimate when it was placed. A suspension that
            // only applied to orders placed after it would be no suspension.
            assertThat(PreTradeChecks.beforePricing(instrument(AssetClass.EQUITY, true),
                            account("530000", AccountStatus.SUSPENDED)),
                    is(Optional.of(RejectReason.ACCOUNT_NOT_ACTIVE)));
        }

        @Test
        @DisplayName("a closed account does not trade either")
        void closedAccountDoesNotTrade() {
            assertThat(PreTradeChecks.beforePricing(instrument(AssetClass.EQUITY, true),
                            account("0", AccountStatus.CLOSED)),
                    is(Optional.of(RejectReason.ACCOUNT_NOT_ACTIVE)));
        }

        @Test
        @DisplayName("a tradable equity on an active account gets as far as the quote")
        void tradableEquityPasses() {
            assertThat(PreTradeChecks.beforePricing(instrument(AssetClass.EQUITY, true), active()),
                    is(Optional.empty()));
        }

        @Test
        @DisplayName("the instrument is reported before the account, being the more specific fact")
        void instrumentOutranksAccount() {
            // Both are wrong. The customer can do nothing about a delisting and
            // something about a suspension, so name the delisting.
            assertThat(PreTradeChecks.beforePricing(instrument(AssetClass.EQUITY, false),
                            account("530000", AccountStatus.SUSPENDED)),
                    is(Optional.of(RejectReason.INSTRUMENT_NOT_TRADABLE)));
        }
    }

    @Nested
    @DisplayName("at execution, because these need the price the order actually fills at")
    class AtExecution {

        @Test
        @DisplayName("a BUY the account can no longer afford is rejected at execution")
        void unaffordableBuyIsRejected() {
            // MRF.NS at 125,121.48. Four fit in 530,000 and five do not, and
            // the customer placed the order when they had more cash.
            OrderSnapshot order = buy("5", "126000.00");

            assertThat(PreTradeChecks.atExecution(order, account("530000", AccountStatus.ACTIVE),
                            Optional.empty(), new BigDecimal("125121.4800")),
                    is(Optional.of(RejectReason.INSUFFICIENT_FUNDS)));
        }

        @Test
        @DisplayName("the re-check uses the executed price, not the limit price")
        void reCheckUsesExecutedPrice() {
            // 92. Checking against the limit would reject an order that is
            // affordable.
            OrderSnapshot order = buy("4", "126000.00");

            assertThat(PreTradeChecks.atExecution(order, account("503000", AccountStatus.ACTIVE),
                            Optional.empty(), new BigDecimal("125121.4800")),
                    is(Optional.empty()));
        }

        @Test
        @DisplayName("available funds are net of blocked funds, not the raw balance")
        void affordabilityIsNetOfBlockedFunds() {
            // Account 3 in the seed: 750,000 balance, 220,000 blocked. An
            // order costing 600,000 fits the balance and not the available
            Account seeded = new Account(3L, "ACC-000003", 3L,
                    new BigDecimal("750000"), new BigDecimal("220000"),
                    AccountStatus.ACTIVE, 7);

            assertThat(PreTradeChecks.atExecution(buy("4", "160000.00"), seeded,
                            Optional.empty(), new BigDecimal("150000.0000")),
                    is(Optional.of(RejectReason.INSUFFICIENT_FUNDS)));
        }

        @Test
        @DisplayName("a BUY that still fits is allowed through")
        void affordableBuyPasses() {
            assertThat(PreTradeChecks.atExecution(buy("4", "126000.00"),
                            account("530000", AccountStatus.ACTIVE),
                            Optional.empty(), new BigDecimal("125121.4800")),
                    is(Optional.empty()));
        }

        @Test
        @DisplayName("a SELL whose holding shrank below the order is rejected")
        void shrunkHoldingIsRejected() {
            // Ten were held when the order was placed; four are held now,
            // because another order sold six while this one waited.
            assertThat(PreTradeChecks.atExecution(sell("10"), active(),
                            Optional.of(holding("4")), new BigDecimal("125083.9400")),
                    is(Optional.of(RejectReason.INSUFFICIENT_HOLDINGS)));
        }

        @Test
        @DisplayName("a SELL with no holding at all is the same answer: nothing to deliver")
        void missingHoldingIsRejected() {
            assertThat(PreTradeChecks.atExecution(sell("10"), active(),
                            Optional.empty(), new BigDecimal("125083.9400")),
                    is(Optional.of(RejectReason.INSUFFICIENT_HOLDINGS)));
        }

        @Test
        @DisplayName("a SELL of exactly the whole holding is allowed: the boundary is inclusive")
        void sellingTheEntireHoldingPasses() {
            assertThat(PreTradeChecks.atExecution(sell("10"), active(),
                            Optional.of(holding("10")), new BigDecimal("125083.9400")),
                    is(Optional.empty()));
        }

        @Test
        @DisplayName("a SELL is never rejected for funds: selling brings cash in")
        void sellIsNotCheckedForFunds() {
            assertThat(PreTradeChecks.atExecution(sell("10"), account("0", AccountStatus.ACTIVE),
                            Optional.of(holding("10")), new BigDecimal("125083.9400")),
                    is(Optional.empty()));
        }
    }

    // ------------------------------------------------------------ fixtures

    private static Instrument instrument(AssetClass assetClass, boolean tradable) {
        return new Instrument(1L, "MRF.NS", "MRF Limited", assetClass, "INR", tradable);
    }

    private static Account active() {
        return account("530000", AccountStatus.ACTIVE);
    }

    private static Account account(String available, AccountStatus status) {
        return new Account(3L, "ACC-000003", 3L,
                new BigDecimal(available), BigDecimal.ZERO, status, 7);
    }

    private static Position holding(String quantity) {
        return new Position(1L, 3L, 1L, new BigDecimal(quantity), new BigDecimal("120000.0000"));
    }

    private static OrderSnapshot buy(String quantity, String limit) {
        return order(OrderSide.BUY, quantity, limit);
    }

    private static OrderSnapshot sell(String quantity) {
        return order(OrderSide.SELL, quantity, "100000.00");
    }

    private static OrderSnapshot order(OrderSide side, String quantity, String limit) {
        return new OrderSnapshot(UUID.randomUUID(), 3L, 1L, "MRF.NS", side,
                new BigDecimal(quantity), new BigDecimal(limit), OrderStatus.NEW);
    }
}
