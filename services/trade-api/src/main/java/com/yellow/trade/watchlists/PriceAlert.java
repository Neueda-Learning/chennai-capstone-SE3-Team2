package com.yellow.trade.watchlists;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One price alert and what has happened to it.
 *
 * @param triggeredPrice the price on the quote that crossed it
 * @param notificationId the notification its firing queued; its delivery is on the inbox
 */
public record PriceAlert(long id, String symbol, AlertDirection direction, BigDecimal threshold, AlertStatus status,
                         Instant createdAt, Instant triggeredAt, BigDecimal triggeredPrice, UUID notificationId) {
}
