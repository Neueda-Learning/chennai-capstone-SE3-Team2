package com.yellow.trade.notifications;

/** What a notification is about: the three order outcomes on trade-events, and a crossed price alert. */
public enum NotificationKind {
    ORDER_FILLED,
    ORDER_REJECTED,
    ORDER_CANCELLED,
    PRICE_ALERT
}
