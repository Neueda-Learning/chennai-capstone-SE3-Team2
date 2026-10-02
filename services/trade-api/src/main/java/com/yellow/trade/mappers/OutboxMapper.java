package com.yellow.trade.mappers;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * outbox_event. Written in the same transaction as the change an event
 * announces, so a rolled-back change publishes nothing and a committed one
 * cannot be lost. OutboxRelay publishes them.
 */
@Mapper
public interface OutboxMapper {

    @Insert("""
            INSERT INTO outbox_event (event_id, topic, message_key, envelope)
            VALUES (CAST(#{eventId} AS uuid), #{topic}, #{messageKey}, CAST(#{envelope} AS jsonb))
            """)
    int insert(@Param("eventId") String eventId,
               @Param("topic") String topic,
               @Param("messageKey") String messageKey,
               @Param("envelope") String envelope);

    /**
     * Oldest unpublished rows, locked until the caller's transaction ends.
     * SKIP LOCKED lets a second instance relay a different batch rather than
     * wait on this one, or send the same rows twice.
     */
    @Select("""
            SELECT event_id::text AS event_id, topic, message_key, envelope::text AS envelope, attempts
            FROM outbox_event
            WHERE published_at IS NULL
            ORDER BY created_at
            LIMIT #{limit}
            FOR UPDATE SKIP LOCKED
            """)
    List<OutboxRow> lockBatch(@Param("limit") int limit);

    @Update("""
            UPDATE outbox_event
               SET published_at = now(),
                   last_error   = NULL
             WHERE event_id = CAST(#{eventId} AS uuid)
            """)
    int markPublished(@Param("eventId") String eventId);

    @Update("""
            UPDATE outbox_event
               SET attempts   = attempts + 1,
                   last_error = #{error}
             WHERE event_id = CAST(#{eventId} AS uuid)
            """)
    int recordFailure(@Param("eventId") String eventId, @Param("error") String error);
}
