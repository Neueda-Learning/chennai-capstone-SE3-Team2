package com.yellow.trade.mappers;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * client_account, and the only writes the platform makes to it.
 *
 * Every value arriving from outside is bound with #{}, which becomes a JDBC
 * bind parameter: the driver sends the statement and the value separately, so
 * nothing a caller sends can change what the statement does. No statement
 * here uses the interpolating form, and no column name or sort direction in
 * this interface would need it.
 */
@Mapper
public interface AccountMapper {

    /**
     * The holder's name lives on client_profile, which is 1:1 with the
     * account, so an inner join is right: an account with no profile is a
     * broken row, not an account with an anonymous holder.
     */
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

    /**
     * Debits cash for a filled buy, under an optimistic lock.
     *
     * The version the row was read at is part of the WHERE clause and the
     * write increments it in the same statement, so the database serialises
     * two concurrent writers: the first affects one row, the second affects
     * none. Zero is not success -- the caller refuses the order with ORD-409.
     *
     * Returns the affected row count rather than void, because a mapper
     * returning void has thrown away the only evidence that anything happened.
     *
     * ck_client_account_balance_non_negative is the second line of defence:
     * if the arithmetic above this ever let a debit overdraw the account, the
     * database refuses the row rather than recording it.
     */
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

    /**
     * Credits the proceeds of a sell, under the same lock. Selling is a write
     * to the account row like any other, so it takes the version with it.
     */
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
