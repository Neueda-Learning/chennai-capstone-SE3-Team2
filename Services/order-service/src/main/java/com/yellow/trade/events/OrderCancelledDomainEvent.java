package com.yellow.trade.events;

import com.yellow.trade.mappers.OrderRow;

import java.time.Instant;

/**
 * An order a customer cancelled, raised inside the cancel's transaction once
 * the conditional update has moved it off NEW. OrderCancelledAnnouncer turns
 * it into ORDER_CANCELLED on trade-events, through the outbox.
 *
 * @param order       the order as it was read before the cancel
 * @param symbol      its instrument, as trade-events names it
 * @param cancelledAt when the cancel ran
 */
public record OrderCancelledDomainEvent(OrderRow order, String symbol, Instant cancelledAt) {
}
