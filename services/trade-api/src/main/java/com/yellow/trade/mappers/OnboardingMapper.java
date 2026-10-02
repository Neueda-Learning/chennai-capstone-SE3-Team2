package com.yellow.trade.mappers;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

/**
 * Creates a customer. The two inserts use ON CONFLICT rather than letting a
 * unique constraint fail: a constraint failure makes Postgres write the
 * offending PAN or email into its own server log, and a duplicate is a normal
 * outcome here, not an error.
 */
@Mapper
public interface OnboardingMapper {

    /** Taken first, so account_ref and demat_id can be derived from it. */
    @Select("SELECT nextval(pg_get_serial_sequence('client_account', 'client_id'))")
    long nextClientId();

    /** 0 rows when the PAN is already registered. */
    @Insert("""
            INSERT INTO client_account (client_id, account_ref, pan, demat_id, kyc_status, status)
            OVERRIDING SYSTEM VALUE
            VALUES (#{clientId}, #{accountRef}, #{pan}, #{dematId}, 'PENDING', 'ACTIVE')
            ON CONFLICT (pan) DO NOTHING
            """)
    int insertAccount(@Param("clientId") long clientId,
                      @Param("accountRef") String accountRef,
                      @Param("pan") String pan,
                      @Param("dematId") String dematId);

    /** 0 rows when the email is already registered. */
    @Insert("""
            INSERT INTO client_profile (client_id, name, dob, email, phone_number, address)
            VALUES (#{clientId}, #{name}, #{dob}, #{email}, #{phoneNumber}, #{address})
            ON CONFLICT (email) DO NOTHING
            """)
    int insertProfile(@Param("clientId") long clientId,
                      @Param("name") String name,
                      @Param("dob") LocalDate dob,
                      @Param("email") String email,
                      @Param("phoneNumber") String phoneNumber,
                      @Param("address") String address);
}
