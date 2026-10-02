package com.yellow.trade.mappers;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

/** kyc_verification. Records outcomes only -- the personal data stays in client_profile. */
@Mapper
public interface KycMapper {

    /** One verification per customer: client_id is the primary key. */
    @Insert("INSERT INTO kyc_verification (client_id) VALUES (#{clientId})")
    int insertPending(@Param("clientId") long clientId);

    /** Pending checks old enough to run, oldest first. Served by ix_kyc_verification_pending. */
    @Select("""
            SELECT client_id
            FROM kyc_verification
            WHERE status = 'PENDING'
              AND submitted_at <= #{cutoff}
            ORDER BY submitted_at
            LIMIT #{limit}
            """)
    List<Long> findDue(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    /** What the checks need, read when they run rather than stored with the verification. */
    @Select("""
            SELECT ca.pan, cp.name, cp.dob
            FROM client_account ca
            JOIN client_profile cp ON cp.client_id = ca.client_id
            WHERE ca.client_id = #{clientId}
            """)
    ApplicantRow findApplicant(@Param("clientId") long clientId);

    /**
     * Records the decision, guarded on PENDING like settleIfNew: 0 rows means
     * another run decided it first, and the caller writes nothing else.
     */
    @Update("""
            UPDATE kyc_verification
               SET status     = #{status},
                   reason     = #{reason},
                   checks     = CAST(#{checks} AS jsonb),
                   decided_at = now()
             WHERE client_id = #{clientId}
               AND status    = 'PENDING'
            """)
    int decide(@Param("clientId") long clientId,
               @Param("status") String status,
               @Param("reason") String reason,
               @Param("checks") String checks);

    /**
     * Mirrors the decision onto the account the trading gate reads. Not a
     * money change, so the account's version is left alone: bumping it would
     * fail a concurrent balance update for nothing.
     */
    @Update("""
            UPDATE client_account
               SET kyc_status = #{status}
             WHERE client_id  = #{clientId}
               AND kyc_status = 'PENDING'
            """)
    int setAccountKycStatus(@Param("clientId") long clientId, @Param("status") String status);
}
