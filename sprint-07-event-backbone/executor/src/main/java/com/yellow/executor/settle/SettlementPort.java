package com.yellow.executor.settle;

import com.yellow.executor.fill.FillDecision;
import com.yellow.executor.fill.OrderSnapshot;

/**
 * Writes a decision down, once.
 *
 * <p>THE SEAM BETWEEN STORY 610 AND STORY 611. Story 610 ships
 * {@link GuardedSettlement}, which does the one write that makes a decision
 * durable and a duplicate harmless. Story 611 replaces the IMPLEMENTATION and
 * keeps this interface: it adds the cash movement, the position write, the
 * optimistic-lock retry and the publish to trade-events, all inside one
 * transaction with the guarded transition as its first write.
 *
 * <p>Whoever picks up 611: the contract you must preserve is that the guarded
 * UPDATE stays FIRST, that zero rows affected returns
 * {@link SettlementResult#ALREADY_SETTLED} having changed and published
 * nothing, and that the event is published after the commit while the offset is
 * acknowledged after the publish. Publishing before the commit risks an event
 * for a transaction that rolled back. Acknowledging before publishing risks an
 * order that settled in Postgres and told nobody.
 */
public interface SettlementPort {

    /**
     * @param decision fill at a price, or reject with a reason
     * @param order    the order as it was loaded
     * @return whether this delivery was the one that settled it
     */
    SettlementResult settle(FillDecision decision, OrderSnapshot order);
}
