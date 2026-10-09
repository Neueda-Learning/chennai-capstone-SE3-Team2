package com.yellow.trade.mappers;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * client_profile, read-only, for a module that needs to reach the customer:
 * the profile is the one place the platform keeps their address, and a module
 * reads it here rather than keeping a copy (Sprint 10, decision log 0003).
 */
@Mapper
public interface ProfileMapper {

    /** null when there is no profile, or no address on it. */
    @Select("SELECT email FROM client_profile WHERE client_id = #{clientId}")
    String findEmail(@Param("clientId") long clientId);
}
