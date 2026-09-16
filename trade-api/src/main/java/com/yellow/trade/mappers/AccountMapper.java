package com.yellow.trade.mappers;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;


@Mapper
public interface AccountMapper {
    @Select("""
            SELECT ca.client_id,
                   ca.account_ref,
                   cp.name AS holder_name,
                   ca.status,
                   ca.balance,
                   ca.blocked_funds,
                   ca.version,
                   ca.created_at,
                   ca.updated_at
            FROM client_account ca
            JOIN client_profile cp ON cp.client_id = ca.client_id
            WHERE ca.client_id = #{accountId}
            """)
    AccountRow findById(@Param("accountId") Long accountId);
    @Update("""
            UPDATE client_account
               SET balance = balance - #{amount},
                   version = version + 1
             WHERE client_id = #{accountId}
               AND version   = #{expectedVersion}
            """)
    int debitBalance(@Param("accountId") Long accountId,
                     @Param("amount") BigDecimal amount,
                     @Param("expectedVersion") int expectedVersion);

    @Update("""
            UPDATE client_account
               SET balance = balance + #{amount},
                   version = version + 1
             WHERE client_id = #{accountId}
               AND version   = #{expectedVersion}
            """)
    int creditBalance(@Param("accountId") Long accountId,
                      @Param("amount") BigDecimal amount,
                      @Param("expectedVersion") int expectedVersion);

}
