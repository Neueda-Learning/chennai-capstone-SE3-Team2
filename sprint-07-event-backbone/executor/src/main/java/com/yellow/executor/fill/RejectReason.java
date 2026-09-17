package com.yellow.executor.fill;

/**
 * Why an order did not fill. Travels to {@code trade-events} as the payload's
 * {@code reason}, which the contract types as a free string with examples
 * rather than a closed enum.
 *
 * <p>The split that matters to a consumer is permanent against transient.
 * {@link #NO_PRICE} is worth retrying by hand tomorrow; {@link #PRICE_NOT_MET}
 * never is. A downstream consumer has to be able to tell those apart from the
 * event alone, which is why they are not one value.
 */
public enum RejectReason {

    /** Rule 6, re-checked at the executed price against the balance as it now is. */
    INSUFFICIENT_FUNDS(true),

    /** Rule 7, re-checked at execution. The holding shrank while the order sat on the topic. */
    INSUFFICIENT_HOLDINGS(true),

    /** Outside the marketable range: a BUY below the ask, or a SELL above the bid. */
    PRICE_NOT_MET(true),

    /** The instrument was delisted or suspended between acceptance and execution. */
    INSTRUMENT_NOT_TRADABLE(true),

    /**
     * No two-sided market exists for this instrument, and none ever will.
     * Mutual funds: units are allotted at a NAV struck once after close, not
     * bought from a counterparty quoting a bid and an ask, so the fill rule has
     * nothing to evaluate. Distinct from {@link #NO_PRICE}, which is temporary.
     */
    INSTRUMENT_NOT_PRICEABLE(true),

    /** Suspended or closed after the order was accepted. It still must not trade. */
    ACCOUNT_NOT_ACTIVE(true),

    /**
     * No price could be obtained: Fauxnance unreachable, out of quota, or
     * serving a stale quote after the retry budget was spent.
     *
     * <p>This is a business outcome, not a message-processing failure. The
     * message is never dead-lettered for it -- doing so would leave the order
     * at NEW for ever, and a customer watching an order that never resolves is
     * worse served than one told it was rejected.
     */
    NO_PRICE(false);

    private final boolean permanent;

    RejectReason(boolean permanent) {
        this.permanent = permanent;
    }

    /**
     * True when retrying this order unchanged could never succeed.
     */
    public boolean isPermanent() {
        return permanent;
    }
}
