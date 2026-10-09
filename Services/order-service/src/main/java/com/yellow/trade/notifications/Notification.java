package com.yellow.trade.notifications;

import com.yellow.trade.preferences.api.AlertChannel;

import java.time.Instant;
import java.util.UUID;

/**
 * One notification as the inbox shows it (openapi/notifications.yaml).
 *
 * @param channel     where it went, resolved from preferences when it was sent; null while QUEUED
 * @param destination the address it went to, masked; null for IN_APP and while QUEUED
 */
public record Notification(UUID id, NotificationKind kind, String subject, String body, AlertChannel channel,
                           String destination, DeliveryStatus status, Instant createdAt, Instant sentAt,
                           Instant readAt) {

    static Notification of(NotificationRow row) {
        return new Notification(row.getNotificationId(), NotificationKind.valueOf(row.getKind()), row.getSubject(),
                row.getBody(), row.getChannel() == null ? null : AlertChannel.valueOf(row.getChannel()),
                row.getDestination(), DeliveryStatus.valueOf(row.getStatus()), row.getCreatedAt(), row.getSentAt(),
                row.getReadAt());
    }
}
