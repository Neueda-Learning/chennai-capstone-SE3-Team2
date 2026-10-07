package com.yellow.trade.notifications;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The customer's notification history, as openapi/notifications.yaml
 * describes. Under /api/v1/, so the token filter has verified the token; the
 * service decides whether this caller may reach this account. Nothing here
 * sends: what watchlists calls is a Java interface, never a route.
 */
@RestController
@RequestMapping("/api/v1/accounts")
@Validated
public class NotificationsController {

    private final NotificationService notifications;

    public NotificationsController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping("/{id}/notifications")
    public List<Notification> history(@PathVariable("id") @Min(1) long accountId,
                                      @RequestParam(name = "limit", defaultValue = "50") @Min(1) @Max(100) int limit) {
        return notifications.history(accountId, limit);
    }

    @GetMapping("/{id}/notifications/unread")
    public UnreadCount unread(@PathVariable("id") @Min(1) long accountId) {
        return notifications.unread(accountId);
    }

    @PostMapping("/{id}/notifications/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable("id") @Min(1) long accountId,
                         @PathVariable("notificationId") UUID notificationId) {
        notifications.markRead(accountId, notificationId);
    }
}
