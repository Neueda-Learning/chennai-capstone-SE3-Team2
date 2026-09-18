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

    /**
     * Reserves cash against an accepted order. Sprint 7.
     *
     * <p>The balance does not move: the money is still the customer's, it is
     * simply no longer available to a second order. {@code availableFunds()} is
     * {@code balance - blocked_funds}, so business rule 6 sees the reduction
     * immediately and the next order is checked against what is genuinely left.
     *
     * <p>Without this, an order accepted but not yet executed reserves nothing,
     * and eight concurrent buys of five thousand against eight thousand
     * available are all accepted — each one passing rule 6 against a balance
     * nothing is decrementing. The executor rejects all but the first at
     * execution, so no money is overspent, but the customer collects seven
     * rejections for a reason that was knowable when they pressed the button.
     *
     * <p>Under the same optimistic lock as every other write to this row.
     * Zero rows affected means somebody else moved the account first.
     */
    @Update("""
            UPDATE client_account
               SET blocked_funds = blocked_funds + #{amount},
                   version       = version + 1
             WHERE client_id = #{accountId}
               AND version   = #{expectedVersion}
            """)
    int blockFunds(@Param("accountId") Long accountId,
                   @Param("amount") BigDecimal amount,
                   @Param("expectedVersion") int expectedVersion);

    /**
     * Returns a reservation to the available balance. Sprint 7.
     *
     * <p>Called when an order stops being live without the executor settling
     * it — today that is a customer cancelling. The executor releases its own
     * reservations inside the settlement transaction, because there the release
     * and the debit have to happen together or not at all.
     *
     * <p>EXACTLY ONCE PER ORDER, and that is the whole discipline here. Release
     * twice and {@code blocked_funds} goes negative and
     * {@code ck_client_account_blocked_non_negative} refuses the write; never
     * release and the reservation is stranded for ever, so the account slowly
     * loses the ability to buy anything while appearing to hold cash. The
     * guard is that both callers act only when a conditional UPDATE on the
     * order row reports that THEY were the one who moved it off NEW.
     */
    @Update("""
            UPDATE client_account
               SET blocked_funds = blocked_funds - #{amount},
                   version       = version + 1
             WHERE client_id = #{accountId}
               AND version   = #{expectedVersion}
            """)
    int releaseFunds(@Param("accountId") Long accountId,
                     @Param("amount") BigDecimal amount,
                     @Param("expectedVersion") int expectedVersion);

}
