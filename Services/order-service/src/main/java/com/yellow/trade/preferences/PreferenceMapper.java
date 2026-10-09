package com.yellow.trade.preferences;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** pref_preference: this module's one table, and only this module's. */
@Mapper
public interface PreferenceMapper {

    @Select("""
            SELECT client_id, default_account_id, landing_screen, alert_channel, updated_at
            FROM pref_preference
            WHERE client_id = #{clientId}
            """)
    PreferenceRow findByClient(@Param("clientId") long clientId);

    /** A customer's first save inserts; every later one replaces it. */
    @Insert("""
            INSERT INTO pref_preference (client_id, default_account_id, landing_screen, alert_channel, updated_at)
            VALUES (#{clientId}, #{defaultAccountId}, #{landingScreen}, #{alertChannel}, #{updatedAt})
            ON CONFLICT (client_id) DO UPDATE
               SET default_account_id = EXCLUDED.default_account_id,
                   landing_screen     = EXCLUDED.landing_screen,
                   alert_channel      = EXCLUDED.alert_channel,
                   updated_at         = EXCLUDED.updated_at
            """)
    void upsert(PreferenceRow row);
}
