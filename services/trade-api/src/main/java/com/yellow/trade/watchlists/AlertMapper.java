package com.yellow.trade.watchlists;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** watch_alert: this module's table, and only this module's. */
@Mapper
public interface AlertMapper {

    String COLUMNS = """
            SELECT a.alert_id, a.client_id, a.instrument_id,
                   COALESCE(e.ticker, m.scheme_code) AS symbol,
                   a.direction, a.threshold, a.status, a.created_at,
                   a.triggered_at, a.triggered_price, a.notification_id
            FROM watch_alert a
            JOIN instrument i       ON i.instrument_id = a.instrument_id
            LEFT JOIN equity e      ON e.instrument_id = i.instrument_id
            LEFT JOIN mutual_fund m ON m.instrument_id = i.instrument_id
            """;

    @Select(COLUMNS + " WHERE a.client_id = #{clientId} ORDER BY a.created_at DESC, a.alert_id DESC")
    List<AlertRow> findForClient(@Param("clientId") long clientId);

    /** This account's alert, or null: another account's is not found, the same as one that does not exist. */
    @Select(COLUMNS + " WHERE a.alert_id = #{alertId} AND a.client_id = #{clientId}")
    AlertRow findOwned(@Param("clientId") long clientId, @Param("alertId") long alertId);

    @Select("SELECT count(*) FROM watch_alert WHERE client_id = #{clientId} AND status = 'ACTIVE'")
    int countActive(@Param("clientId") long clientId);

    @Insert("""
            INSERT INTO watch_alert (client_id, instrument_id, direction, threshold, created_at)
            VALUES (#{clientId}, #{instrumentId}, #{direction}, #{threshold}, #{createdAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "alertId", keyColumn = "alert_id")
    int insert(AlertRow row);

    @Update("""
            UPDATE watch_alert SET status = 'CANCELLED'
             WHERE alert_id = #{alertId} AND client_id = #{clientId}
            """)
    int cancel(@Param("clientId") long clientId, @Param("alertId") long alertId);

    /** ACTIVE again, its last firing cleared; the notification it queued stays in the inbox. */
    @Update("""
            UPDATE watch_alert
               SET status = 'ACTIVE', triggered_at = NULL, triggered_price = NULL, notification_id = NULL
             WHERE alert_id = #{alertId} AND client_id = #{clientId}
            """)
    int rearm(@Param("clientId") long clientId, @Param("alertId") long alertId);

    /**
     * The hot path, on every quote: the ACTIVE alerts on this instrument the
     * price crosses, through ix_watch_alert_active. Locked until the caller's
     * transaction ends, so two consumers seeing the same quote cannot both
     * fire one alert; SKIP LOCKED lets the second move on.
     */
    @Select("""
            SELECT a.alert_id, a.client_id, a.instrument_id, a.direction, a.threshold, a.status, a.created_at
            FROM watch_alert a
            WHERE a.instrument_id = #{instrumentId}
              AND a.status = 'ACTIVE'
              AND ((a.direction = 'ABOVE' AND a.threshold <= #{price})
                OR (a.direction = 'BELOW' AND a.threshold >= #{price}))
            ORDER BY a.alert_id
            FOR UPDATE SKIP LOCKED
            """)
    List<AlertRow> lockCrossed(@Param("instrumentId") long instrumentId, @Param("price") BigDecimal price);

    @Update("""
            UPDATE watch_alert
               SET status = 'TRIGGERED', triggered_at = #{triggeredAt}, triggered_price = #{price},
                   notification_id = #{notificationId}
             WHERE alert_id = #{alertId} AND status = 'ACTIVE'
            """)
    int markTriggered(@Param("alertId") long alertId, @Param("price") BigDecimal price,
                      @Param("triggeredAt") Instant triggeredAt, @Param("notificationId") UUID notificationId);
}
