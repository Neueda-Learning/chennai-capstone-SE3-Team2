package com.yellow.executor.settle;

/**
 * What happened when the executor tried to write a decision down.
 *
 * <p>Three outcomes, and telling them apart is what keeps a duplicate delivery
 * harmless. They are not "success" and "failure": ALREADY_SETTLED is a normal
 * Tuesday. Kafka guarantees at-least-once deliberately, because the alternative
 * is a broker that occasionally loses a trade, so the same order arrives twice
 * during a rebalance, after a crash between the database commit and the offset
 * commit, or when somebody replays a topic to debug something.
 */
public enum SettlementResult {

    /** The row moved from NEW to a terminal status. Publish the event, then acknowledge. */
    SETTLED,

    /**
     * The guarded UPDATE matched zero rows: another delivery settled this order
     * already. Nothing was written and nothing must be published -- a second
     * event on trade-events would tell every downstream consumer the trade
     * happened twice, which is the same bug one service further along.
     * Acknowledge and move on.
     */
    ALREADY_SETTLED,

    /**
     * The optimistic lock on the account was lost more times than the retry
     * budget allows. Story 611 returns this; story 610's settlement cannot
     * produce it, because it does not touch the account.
     *
     * <p>Unlike the other two this IS an error, and it is the one class of
     * failure that should be retried from the topic rather than resolved: the
     * order is still NEW and still deserves an answer.
     */
    LOCK_EXHAUSTED
}
