package com.yellow.trade.notifications.api;

/**
 * Has a crossed price alert delivered: the one seam the notifications module
 * publishes (Sprint 10, decision log 0002). The watchlists module calls it, in
 * process, from the transaction that marks the alert triggered, so an alert
 * never reads triggered without a notification queued (decision log 0008).
 * It is not an HTTP route, so no customer can send themselves anything.
 *
 * The notification is recorded QUEUED in the caller's transaction and sent
 * after it commits, on the channel the preferences module resolves at that
 * moment; the caller never chooses a channel. Keyed on {@link AlertNotice#eventId()}:
 * the same quote delivered twice queues one notification, and the second
 * call answers the first one's receipt with {@code duplicate} true.
 */
public interface AlertDelivery {

    DeliveryReceipt deliver(AlertNotice notice);
}
