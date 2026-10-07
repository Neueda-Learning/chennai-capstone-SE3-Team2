package com.yellow.trade.notifications;

import com.yellow.trade.notifications.NotificationExceptions.MailDeliveryException;
import com.yellow.trade.preferences.api.AlertChannel;
import com.yellow.trade.preferences.api.ChannelResolver;
import com.yellow.trade.preferences.api.ResolvedChannel;
import com.yellow.trade.security.Masking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Sends what the ledger has queued. The channel is resolved through the
 * preferences module for every message, at the moment it is sent, and
 * recorded on it: a preference changed while a message waited is honoured,
 * and the record shows where it went (decision log 0006).
 *
 * Preferences not answering leaves the message QUEUED for a later pass,
 * never sent on a guess. The mail server refusing is retried with a growing
 * wait, then FAILED. Nothing here stalls the consumer: it only ever reads
 * rows the consumer has already committed.
 */
@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    /** Attempts before a message is FAILED: the fifth refusal ends it. */
    static final int MAX_ATTEMPTS = 5;
    /** The wait after the n-th failed attempt is n times this: 30 s, 1 min, 1.5 min, 2 min. */
    static final Duration RETRY_STEP = Duration.ofSeconds(30);
    private static final int MAX_ERROR_LENGTH = 500;

    private final NotificationMapper notifications;
    private final ChannelResolver channels;
    private final NotificationMailSender mail;
    private final Clock clock;
    private final int batchSize;

    public NotificationDispatcher(NotificationMapper notifications, ChannelResolver channels,
                                  NotificationMailSender mail, Clock clock,
                                  @Value("${notifications.dispatch.batch-size:20}") int batchSize) {
        this.notifications = notifications;
        this.channels = channels;
        this.mail = mail;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    /** One pass over what is due. @return how many were sent */
    @Transactional
    public int dispatchOnce() {
        Instant now = clock.instant();
        List<NotificationRow> due = notifications.lockDue(now, batchSize);
        int sent = 0;
        for (NotificationRow row : due) {
            if (send(row, now)) {
                sent++;
            }
        }
        return sent;
    }

    private boolean send(NotificationRow row, Instant now) {
        ResolvedChannel channel;
        try {
            channel = channels.resolve(row.getClientId());
        } catch (ChannelResolver.UnknownAccountException e) {
            notifications.markFailed(row.getNotificationId(), null, null, "no such account");
            log.warn("notification {} FAILED: account {} does not exist", row.getNotificationId(), row.getClientId());
            return false;
        } catch (RuntimeException e) {
            failedAttempt(row, null, null, "the channel could not be resolved: " + e.getClass().getSimpleName(), now);
            return false;
        }

        if (channel.channel() == AlertChannel.IN_APP) {
            notifications.markSent(row.getNotificationId(), AlertChannel.IN_APP.name(), null, now);
            log.info("notification {} delivered IN_APP for account {}", row.getNotificationId(), row.getClientId());
            return true;
        }

        String masked = Masking.email(channel.destination());
        try {
            mail.send(channel.destination(), row.getSubject(), row.getBody());
        } catch (MailDeliveryException e) {
            failedAttempt(row, AlertChannel.EMAIL.name(), masked, e.getMessage(), now);
            return false;
        }
        notifications.markSent(row.getNotificationId(), AlertChannel.EMAIL.name(), masked, now);
        log.info("notification {} sent by EMAIL for account {}", row.getNotificationId(), row.getClientId());
        return true;
    }

    /** Retried later, or FAILED once it has been tried MAX_ATTEMPTS times. The error is ours: it names no address. */
    private void failedAttempt(NotificationRow row, String channel, String masked, String error, Instant now) {
        int attempts = row.getAttempts() + 1;
        String reason = error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
        if (attempts >= MAX_ATTEMPTS) {
            notifications.markFailed(row.getNotificationId(), channel, masked, reason);
            log.warn("notification {} FAILED after {} attempts: {}", row.getNotificationId(), attempts, reason);
            return;
        }
        Instant next = now.plus(RETRY_STEP.multipliedBy(attempts));
        notifications.retryLater(row.getNotificationId(), reason, next);
        log.warn("notification {} not sent, attempt {}: {}; next try at {}", row.getNotificationId(), attempts, reason, next);
    }
}
