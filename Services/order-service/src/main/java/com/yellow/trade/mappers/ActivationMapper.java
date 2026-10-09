package com.yellow.trade.mappers;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.UUID;

/** The trading database, and only the trading database. The auth database is never reached from here. */
@Mapper
public interface ActivationMapper {

    /** A primary-key lookup: client_profile.client_id is the provisioned account id. */
    @Select("SELECT email FROM client_profile WHERE client_id = #{clientId}")
    String findEmail(@Param("clientId") long clientId);

    @Select("SELECT COUNT(*) FROM activation_email WHERE event_id = #{eventId}")
    int countSent(@Param("eventId") UUID eventId);

    @Insert("INSERT INTO activation_email (event_id, client_id) VALUES (#{eventId}, #{clientId}) "
            + "ON CONFLICT (event_id) DO NOTHING")
    int recordSent(@Param("eventId") UUID eventId, @Param("clientId") long clientId);
}
