package com.yellow.trade.mappers;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * fund_transfer, and the money moves that go with it. Every change to an
 * account's cash is one UPDATE that adds or subtracts in place, so it cannot
 * lose a concurrent change, and bumps version so an order holding a stale
 * read of the account fails its optimistic check instead of overwriting.
 * The schema's own CHECKs (balance >= 0, blocked within balance) are the last
 * line: a bug here cannot overdraw an account.
 */
@Mapper
public interface PaymentMapper {

    String TRANSFER_COLUMNS = "transfer_id, client_id, direction, amount, status, reason, created_at, decided_at";

    @Select("SELECT account_number, ifsc, holder_name FROM bank_account WHERE client_id = #{clientId}")
    BankAccountRow findBankAccount(@Param("clientId") long clientId);

    /**
     * A new PENDING transfer, or null when this client has used the key
     * already: ON CONFLICT rather than a failing constraint, so a retry is an
     * ordinary outcome and not an error in the server log.
     */
    @Select("""
            INSERT INTO fund_transfer (client_id, amount, direction, status, idempotency_key)
            VALUES (#{clientId}, #{amount}, #{direction}, 'PENDING', #{idempotencyKey})
            ON CONFLICT (client_id, idempotency_key) DO NOTHING
            RETURNING transfer_id
            """)
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    Long insertPending(@Param("clientId") long clientId,
                       @Param("amount") BigDecimal amount,
                       @Param("direction") String direction,
                       @Param("idempotencyKey") String idempotencyKey);

    @Select("SELECT " + TRANSFER_COLUMNS + " FROM fund_transfer WHERE transfer_id = #{transferId}")
    TransferRow findById(@Param("transferId") long transferId);

    @Select("SELECT " + TRANSFER_COLUMNS + " FROM fund_transfer"
            + " WHERE client_id = #{clientId} AND idempotency_key = #{idempotencyKey}")
    TransferRow findByKey(@Param("clientId") long clientId, @Param("idempotencyKey") String idempotencyKey);

    /** Newest first. */
    @Select("SELECT " + TRANSFER_COLUMNS + " FROM fund_transfer"
            + " WHERE client_id = #{clientId} ORDER BY created_at DESC, transfer_id DESC")
    List<TransferRow> findForClient(@Param("clientId") long clientId);

    /** PENDING transfers old enough to decide, oldest first, leaving out those set aside. */
    @Select("""
            SELECT transfer_id
            FROM fund_transfer
            WHERE status = 'PENDING'
              AND created_at <= #{cutoff}
              AND attempts < #{maxAttempts}
            ORDER BY created_at
            LIMIT #{limit}
            """)
    List<Long> findDue(@Param("cutoff") Instant cutoff,
                       @Param("maxAttempts") int maxAttempts,
                       @Param("limit") int limit);

    /** Guarded on PENDING: 0 rows means another run decided it first. */
    @Update("""
            UPDATE fund_transfer
               SET status       = #{status},
                   reason       = #{reason},
                   reference_id = #{reference},
                   decided_at   = now()
             WHERE transfer_id = #{transferId}
               AND status      = 'PENDING'
            """)
    int decide(@Param("transferId") long transferId,
               @Param("status") String status,
               @Param("reason") String reason,
               @Param("reference") String reference);

    /** Holds a withdrawal's amount out of available cash. 0 rows: not enough available. */
    @Update("""
            UPDATE client_account
               SET blocked_funds = blocked_funds + #{amount}, version = version + 1
             WHERE client_id = #{clientId}
               AND balance - blocked_funds >= #{amount}
            """)
    int hold(@Param("clientId") long clientId, @Param("amount") BigDecimal amount);

    /** A deposit that succeeded. */
    @Update("UPDATE client_account SET balance = balance + #{amount}, version = version + 1"
            + " WHERE client_id = #{clientId}")
    int credit(@Param("clientId") long clientId, @Param("amount") BigDecimal amount);

    /** A withdrawal that succeeded: the held amount leaves the account. */
    @Update("""
            UPDATE client_account
               SET balance = balance - #{amount}, blocked_funds = blocked_funds - #{amount}, version = version + 1
             WHERE client_id = #{clientId}
            """)
    int settleWithdrawal(@Param("clientId") long clientId, @Param("amount") BigDecimal amount);

    /** A withdrawal that failed: the held amount is available again. */
    @Update("UPDATE client_account SET blocked_funds = blocked_funds - #{amount}, version = version + 1"
            + " WHERE client_id = #{clientId}")
    int releaseHold(@Param("clientId") long clientId, @Param("amount") BigDecimal amount);

    /**
     * Counts a failed attempt to decide, after that attempt rolled back. An
     * UPDATE in a @Select so RETURNING gives the new count back: null when the
     * transfer is no longer PENDING.
     */
    @Select("""
            UPDATE fund_transfer
               SET attempts = attempts + 1, last_error = #{error}
             WHERE transfer_id = #{transferId}
               AND status = 'PENDING'
            RETURNING attempts
            """)
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    Integer recordFailure(@Param("transferId") long transferId, @Param("error") String error);
}
