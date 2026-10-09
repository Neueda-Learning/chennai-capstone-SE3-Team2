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
 * moment; the caller never chooses a channel. Keyed on the quote and the alert,
 * {@link AlertNotice#eventId()} with {@link AlertNotice#alertId()}: one quote
 * crossing two customers' alerts queues a notification for each, and the same
 * quote delivered twice for one alert queues one, the second call answering
 * the first one's receipt with {@code duplicate} true.
 *
 * Must be called inside the caller's transaction; a call with none to join is
 * refused (IllegalTransactionStateException) rather than committed on its own.
 */
public interface AlertDelivery {

    DeliveryReceipt deliver(AlertNotice notice);
}
