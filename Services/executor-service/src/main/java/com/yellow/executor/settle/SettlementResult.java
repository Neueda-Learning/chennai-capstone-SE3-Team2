package com.yellow.executor.settle;

/**
 * What happened when the executor tried to write a decision down. Three
 * outcomes, and telling them apart is what keeps a duplicate delivery
 * harmless.
 */
public enum SettlementResult {

    /** The row moved from NEW to a terminal status. Publish the event, then acknowledge. */
    SETTLED,

    /** The guarded UPDATE matched zero rows: another delivery settled this order already. */
    ALREADY_SETTLED,

    /**
     * The optimistic lock on the account was lost more times than the retry
     * budget allows. Story 611 returns this; story 610's settlement cannot
     * produce it, because it does not touch the account.
     */
    LOCK_EXHAUSTED
}
