package com.yellow.executor.checks;

import com.yellow.entities.Account;
import com.yellow.entities.Instrument;
import com.yellow.entities.Position;
import com.yellow.enums.AssetClass;
import com.yellow.executor.fill.OrderSnapshot;
import com.yellow.executor.fill.RejectReason;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Everything checked around the fill rule, in two passes.
 *
 * <p>Pure, like the rule itself: it reads entities it was handed and returns a
 * verdict. The entities are the Sprint 5 domain's, deliberately, because two of
 * these checks ARE Sprint 5 rules and {@link Account#canAfford} and
 * {@link Position#canSell} are where they already live. A second copy here
 * would be a second copy of a rule, drifting from the day it was written.
 *
 * <h2>Why two passes and not one</h2>
 *
 * The first pass runs BEFORE a quote is fetched, and every check in it is one
 * that needs no price. That ordering is not tidiness: a quote costs one of 2000
 * daily requests shared with the poller, and spending one to price an order we
 * already know we are rejecting is a request the fill path does not have later
 * in the day.
 *
 * <p>The second pass runs AFTER the fill rule, because it needs the executed
 * price. Rules 6 and 7 were checked at acceptance against the limit price and
 * an older balance, and both have moved while the order sat on the topic:
 * cash may have left on another order, a holding may have been sold, and the
 * price the order will actually fill at is not the price the customer named.
 * Checking them again here is the difference between an order that was
 * affordable when it was placed and one that is affordable when it settles.
 */
public final class PreTradeChecks {

    private PreTradeChecks() {
    }

    /**
     * What can be decided without a price. Empty means "carry on and fetch a
     * quote".
     *
     * <p>The order of the checks is the order of the answers. An instrument
     * that was delisted is reported as delisted even if the account is also
     * suspended, because the instrument is the more specific fact and the one
     * the customer can do least about.
     */
    public static Optional<RejectReason> beforePricing(Instrument instrument, Account account) {

        // Delisted or suspended between acceptance and execution. Sprint 5's
        // rule 3, re-asked, because is_tradable can change under a resting order.
        if (!instrument.isTradable()) {
            return Optional.of(RejectReason.INSTRUMENT_NOT_TRADABLE);
        }

        // No two-sided market, and never will be. A mutual fund is allotted at
        // a NAV struck after close, not bought from someone quoting a bid and
        // an ask, so there is nothing for the fill rule to compare against and
        // no quote worth spending a request on.
        if (instrument.assetClass() == AssetClass.MUTUAL_FUND) {
            return Optional.of(RejectReason.INSTRUMENT_NOT_PRICEABLE);
        }

        // Suspended AFTER the order was accepted. The order was legitimate when
        // it was placed and must still not trade now: a suspension that only
        // applied to orders placed after it would be no suspension at all.
        if (!account.isActive()) {
            return Optional.of(RejectReason.ACCOUNT_NOT_ACTIVE);
        }

        return Optional.empty();
    }

    /**
     * Rules 6 and 7, re-checked at the price the order would actually fill at.
     *
     * @param position the holding, or empty if the account holds none of this
     *                 instrument
     */
    public static Optional<RejectReason> atExecution(OrderSnapshot order,
                                                     Account account,
                                                     Optional<Position> position,
                                                     BigDecimal executedPrice) {
        if (order.isBuy()) {
            // Rule 6, against the executed price rather than the limit. A BUY
            // fills at the ask, which is at or below the limit, so the cost has
            // if anything fallen -- but the balance may have fallen further.
            BigDecimal consideration = order.considerationAt(executedPrice);
            return account.canAfford(consideration)
                    ? Optional.empty()
                    : Optional.of(RejectReason.INSUFFICIENT_FUNDS);
        }

        // Rule 7. A holding that has gone entirely is the same answer as one
        // that has merely shrunk below the order: there is nothing to deliver.
        return position.filter(held -> held.canSell(order.quantity())).isPresent()
                ? Optional.empty()
                : Optional.of(RejectReason.INSUFFICIENT_HOLDINGS);
    }
}
