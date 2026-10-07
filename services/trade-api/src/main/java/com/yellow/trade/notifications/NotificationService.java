package com.yellow.trade.notifications;

import com.yellow.trade.security.AccountAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * The history, the in-app inbox: every route the caller's own account only,
 * checked before anything is read (ACC-403, logged by AccountAccess).
 */
@Service
public class NotificationService {

    private final NotificationMapper notifications;
    private final AccountAccess access;
    private final Clock clock;

    public NotificationService(NotificationMapper notifications, AccountAccess access, Clock clock) {
        this.notifications = notifications;
        this.access = access;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Notification> history(long accountId, int limit) {
        access.requireOwn(accountId);
        return notifications.findForClient(accountId, limit).stream().map(Notification::of).toList();
    }

    @Transactional(readOnly = true)
    public UnreadCount unread(long accountId) {
        access.requireOwn(accountId);
        return new UnreadCount(notifications.countUnread(accountId));
    }

    @Transactional
    public void markRead(long accountId, UUID notificationId) {
        access.requireOwn(accountId);
        if (notifications.markRead(accountId, notificationId, clock.instant()) == 0) {
            throw new NotificationNotFoundException();
        }
    }
}
