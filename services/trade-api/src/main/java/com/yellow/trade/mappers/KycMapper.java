package com.yellow.trade.mappers;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** kyc_verification. Records outcomes only -- the personal data stays in client_profile. */
@Mapper
public interface KycMapper {

    /** One verification per customer: client_id is the primary key. */
    @Insert("INSERT INTO kyc_verification (client_id) VALUES (#{clientId})")
    int insertPending(@Param("clientId") long clientId);
}
