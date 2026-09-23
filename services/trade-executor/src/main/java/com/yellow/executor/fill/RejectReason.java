package com.yellow.executor.fill;

/**
 * Why an order did not fill. Travels to trade-events as the payload's reason,
 * which the contract types as a free string with examples rather than a closed
 * enum.
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

    /** No two-sided market exists for this instrument, and none ever will. */
    INSTRUMENT_NOT_PRICEABLE(true),

    /** Suspended or closed after the order was accepted. It still must not trade. */
    ACCOUNT_NOT_ACTIVE(true),

    /**
     * No price could be obtained: Fauxnance unreachable, out of quota, or
     * serving a stale quote after the retry budget was spent.
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
