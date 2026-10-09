package com.yellow.trade.notifications;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** notif_notification, the ledger: this module's one table, and only this module's. */
@Mapper
public interface NotificationMapper {

    /**
     * Records a message QUEUED. The source key (the event, and the alert for a
     * price alert) is unique, so a replay inserts nothing: 0 rows.
     */
    @Insert("""
            INSERT INTO notif_notification
                   (notification_id, event_id, alert_id, client_id, kind, subject, body, created_at, next_attempt_at)
            VALUES (#{notificationId}, #{eventId}, #{alertId}, #{clientId}, #{kind}, #{subject}, #{body},
                    #{createdAt}, #{createdAt})
            ON CONFLICT ON CONSTRAINT uq_notif_source DO NOTHING
            """)
    int insertQueued(NotificationRow row);

    @Select("""
            SELECT notification_id
            FROM notif_notification
            WHERE event_id = #{eventId} AND alert_id IS NOT DISTINCT FROM #{alertId}
            """)
    UUID findIdBySource(@Param("eventId") UUID eventId, @Param("alertId") Long alertId);

    @Select("""
            SELECT notification_id, event_id, alert_id, client_id, kind, subject, body, status, channel,
                   destination, attempts, last_error, created_at, sent_at, read_at
            FROM notif_notification
            WHERE client_id = #{clientId}
            ORDER BY created_at DESC, notification_id
            LIMIT #{limit}
            """)
    List<NotificationRow> findForClient(@Param("clientId") long clientId, @Param("limit") int limit);

    @Select("SELECT count(*) FROM notif_notification WHERE client_id = #{clientId} AND read_at IS NULL")
    int countUnread(@Param("clientId") long clientId);

    /** Marks one read, on this account only; one already read keeps its time. 0 rows: not this account's. */
    @Update("""
            UPDATE notif_notification
               SET read_at = COALESCE(read_at, #{readAt})
             WHERE notification_id = #{notificationId} AND client_id = #{clientId}
            """)
    int markRead(@Param("clientId") long clientId, @Param("notificationId") UUID notificationId,
                 @Param("readAt") Instant readAt);

    /**
     * What is waiting and due, oldest first, locked until the caller's
     * transaction ends. SKIP LOCKED lets a second instance take a different
     * batch rather than send the same messages twice.
     */
    @Select("""
            SELECT notification_id, event_id, alert_id, client_id, kind, subject, body, status, channel,
                   destination, attempts, last_error, created_at, sent_at, read_at
            FROM notif_notification
            WHERE status = 'QUEUED' AND next_attempt_at <= #{now}
            ORDER BY next_attempt_at
            LIMIT #{limit}
            FOR UPDATE SKIP LOCKED
            """)
    List<NotificationRow> lockDue(@Param("now") Instant now, @Param("limit") int limit);

    @Update("""
            UPDATE notif_notification
               SET status = 'SENT', channel = #{channel}, destination = #{destination}, sent_at = #{sentAt},
                   attempts = attempts + 1, last_error = NULL
             WHERE notification_id = #{notificationId} AND status = 'QUEUED'
            """)
    int markSent(@Param("notificationId") UUID notificationId, @Param("channel") String channel,
                 @Param("destination") String destination, @Param("sentAt") Instant sentAt);

    @Update("""
            UPDATE notif_notification
               SET attempts = attempts + 1, last_error = #{error}, next_attempt_at = #{nextAttemptAt}
             WHERE notification_id = #{notificationId} AND status = 'QUEUED'
            """)
    int retryLater(@Param("notificationId") UUID notificationId, @Param("error") String error,
                   @Param("nextAttemptAt") Instant nextAttemptAt);

    @Update("""
            UPDATE notif_notification
               SET status = 'FAILED', channel = #{channel}, destination = #{destination},
                   attempts = attempts + 1, last_error = #{error}
             WHERE notification_id = #{notificationId} AND status = 'QUEUED'
            """)
    int markFailed(@Param("notificationId") UUID notificationId, @Param("channel") String channel,
                   @Param("destination") String destination, @Param("error") String error);
}
