package com.yellow.trade.notifications.api;

import java.util.UUID;

/**
 * @param notificationId the notification queued for the alert, which the
 *                       customer's inbox shows with its delivery state
 * @param duplicate      true when this notice had already been delivered and
 *                       nothing new was queued
 */
public record DeliveryReceipt(UUID notificationId, boolean duplicate) {
}
