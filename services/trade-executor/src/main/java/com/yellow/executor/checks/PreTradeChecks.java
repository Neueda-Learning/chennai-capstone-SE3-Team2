package com.yellow.executor.checks;

import com.yellow.entities.Account;
import com.yellow.entities.Instrument;
import com.yellow.entities.Position;
import com.yellow.executor.fill.OrderSnapshot;
import com.yellow.executor.fill.RejectReason;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Everything checked around the fill rule, in two passes. Pure, like the rule
 * itself: it reads entities it was handed and returns a verdict.
 */
public final class PreTradeChecks {

    private PreTradeChecks() {
    }

    /** What can be decided without a price. Empty means "carry on and fetch a quote". */
    public static Optional<RejectReason> beforePricing(Instrument instrument, Account account) {

        // Delisted or suspended between acceptance and execution. Sprint 5's
        // rule 3, re-asked, because is_tradable can change under a resting order.
        if (!instrument.isTradable()) {
            return Optional.of(RejectReason.INSTRUMENT_NOT_TRADABLE);
        }

        // Suspended AFTER the order was accepted.
        if (!account.isActive()) {
            return Optional.of(RejectReason.ACCOUNT_NOT_ACTIVE);
        }

        return Optional.empty();
    }

    /** Rules 6 and 7, re-checked at the price the order would actually fill at. */
    public static Optional<RejectReason> atExecution(OrderSnapshot order,
                                                     Account account,
                                                     Optional<Position> position,
                                                     BigDecimal executedPrice) {
        if (order.isBuy()) {
            // Rule 6, against the executed price rather than the limit.
            BigDecimal consideration = order.considerationAt(executedPrice);
            return spendableBy(order, account).compareTo(consideration) >= 0
                    ? Optional.empty()
                    : Optional.of(RejectReason.INSUFFICIENT_FUNDS);
        }

        // Rule 7. A holding that has gone entirely is the same answer as one
        // that has merely shrunk below the order: there is nothing to deliver.
        return position.filter(held -> held.canSell(order.quantity())).isPresent()
                ? Optional.empty()
                : Optional.of(RejectReason.INSUFFICIENT_HOLDINGS);
    }

    /**
     * The cash a BUY may spend. The Trade REST API blocked the order's
     * reservation when it accepted it, and settlement releases exactly that:
     * it is this order's money, not money held from it. Only what is blocked
     * for anything else -- other orders, withdrawals -- is out of reach.
     *
     * blocked_funds is one number for the whole account, so when it holds less
     * than the reservation, the reservation is not there -- an order accepted
     * before reservations existed, or one released elsewhere -- and none of it
     * is counted back.
     */
    private static BigDecimal spendableBy(OrderSnapshot order, Account account) {
        BigDecimal reservation = order.reservation();
        BigDecimal blocked = account.blockedFunds();
        BigDecimal heldForOthers = blocked.compareTo(reservation) >= 0 ? blocked.subtract(reservation) : blocked;
        return account.balance().subtract(heldForOthers);
    }
}
