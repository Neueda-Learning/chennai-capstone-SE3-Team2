package com.yellow.trade.mappers;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * outbox_event. Written in the same transaction as the change an event
 * announces, so a rolled-back change publishes nothing and a committed one
 * cannot be lost. The relay that publishes these is separate.
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
}
