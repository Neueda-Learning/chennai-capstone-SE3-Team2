package com.yellow.trade.notifications;

import com.yellow.trade.notifications.NotificationMessages.Message;
import com.yellow.trade.notifications.api.AlertDelivery;
import com.yellow.trade.notifications.api.AlertNotice;
import com.yellow.trade.notifications.api.DeliveryReceipt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Where every notification starts: recorded QUEUED, and nothing sent here.
 * The dispatcher sends it after this transaction commits (decision log 0006).
 *
 * Two doors in. The trade-events consumer records an order's outcome; the
 * watchlists module, through {@link AlertDelivery}, records a crossed alert in
 * the same transaction that marks the alert triggered (decision log 0008).
 */
@Service
public class NotificationLedger implements AlertDelivery {

    private static final Logger log = LoggerFactory.getLogger(NotificationLedger.class);

    private final NotificationMapper notifications;
    private final Clock clock;

    public NotificationLedger(NotificationMapper notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    /** @return false when this event was already recorded: a replay, and a no-op */
    @Transactional
    public boolean record(TradeEvent event) {
        Message message = NotificationMessages.forTrade(event);
        NotificationRow row = row(event.eventId(), null, event.accountId(), event.kind(), message);
        if (notifications.insertQueued(row) == 0) {
            log.info("trade event {} already recorded: nothing queued twice", event.eventId());
            return false;
        }
        log.info("notification {} queued: {} for account {}", row.getNotificationId(), event.kind(), event.accountId());
        return true;
    }

    /**
     * Only inside the caller's transaction: an alert must never read
     * triggered without its notification queued, so a call with no
     * transaction to join is refused rather than committed on its own.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public DeliveryReceipt deliver(AlertNotice notice) {
        Message message = NotificationMessages.forAlert(notice);
        NotificationRow row = row(notice.eventId(), notice.alertId(), notice.accountId(), NotificationKind.PRICE_ALERT,
                message);
        if (notifications.insertQueued(row) == 0) {
            UUID first = notifications.findIdBySource(notice.eventId(), notice.alertId());
            log.info("alert {} on quote {} already queued as {}", notice.alertId(), notice.eventId(), first);
            return new DeliveryReceipt(first, true);
        }
        log.info("notification {} queued: alert {} for account {}", row.getNotificationId(), notice.alertId(),
                notice.accountId());
        return new DeliveryReceipt(row.getNotificationId(), false);
    }

    private NotificationRow row(UUID eventId, Long alertId, long accountId, NotificationKind kind, Message message) {
        NotificationRow row = new NotificationRow();
        row.setNotificationId(UUID.randomUUID());
        row.setEventId(eventId);
        row.setAlertId(alertId);
        row.setClientId(accountId);
        row.setKind(kind.name());
        row.setSubject(message.subject());
        row.setBody(message.body());
        row.setCreatedAt(clock.instant());
        return row;
    }
}
