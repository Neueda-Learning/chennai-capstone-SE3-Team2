package com.yellow.trade.notifications;

/**
 * Where a notification is in its delivery, tracked apart from the Kafka
 * offset (decision log 0006): QUEUED once recorded, then SENT, or FAILED once
 * it has been tried as often as it will be.
 */
public enum DeliveryStatus {
    QUEUED,
    SENT,
    FAILED
}
