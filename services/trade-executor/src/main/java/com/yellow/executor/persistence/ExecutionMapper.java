package com.yellow.executor.persistence;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Everything the executor reads and writes. Story 610 added the guarded orders
 * update; story 611 adds the account and position writes.
 */
@Mapper
public interface ExecutionMapper {

    /**
     * The order, with the symbol and the instrument facts the checks need, in
     * one round trip. scheme_code for a fund -- so both joins are outer and
     * COALESCE picks the one that exists.
     */
    @Select("""
            SELECT o.order_id,
                   o.client_id,
                   o.instrument_id,
                   COALESCE(e.ticker, m.scheme_code) AS symbol,
                   o.side,
                   o.quantity,
                   o.price,
                   o.status,
                   i.instrument_type,
                   i.name          AS instrument_name,
                   i.is_tradable   AS tradable
            FROM orders o
            JOIN instrument i       ON i.instrument_id = o.instrument_id
            LEFT JOIN equity      e ON e.instrument_id = o.instrument_id
            LEFT JOIN mutual_fund m ON m.instrument_id = o.instrument_id
            WHERE o.order_id = #{orderId}
            """)
    ExecutableOrderRow findOrder(@Param("orderId") UUID orderId);

    @Select("""
            SELECT client_id,
                   account_ref,
                   status,
                   balance,
                   blocked_funds,
                   version
            FROM client_account
            WHERE client_id = #{accountId}
            """)
    AccountRow findAccount(@Param("accountId") Long accountId);

    @Select("""
            SELECT position_id,
                   client_id,
                   instrument_id,
                   quantity,
                   average_price
            FROM position
            WHERE client_id     = #{accountId}
              AND instrument_id = #{instrumentId}
              AND position_type = #{positionType}
            """)
    PositionRow findPosition(@Param("accountId") Long accountId,
                             @Param("instrumentId") Long instrumentId,
                             @Param("positionType") String positionType);

    /**
     * THE GUARDED STATE TRANSITION. The whole duplicate-handling mechanism is
     * this one statement.
     */
    @Update("""
            UPDATE orders
               SET status           = #{status},
                   fill_price       = #{fillPrice},
                   resolved_at      = #{resolvedAt},
                   rejection_reason = #{rejectionReason}
             WHERE order_id = #{orderId}
               AND status   = 'NEW'
            """)
    int settleIfNew(@Param("orderId") UUID orderId,
                    @Param("status") String status,
                    @Param("fillPrice") BigDecimal fillPrice,
                    @Param("resolvedAt") Instant resolvedAt,
                    @Param("rejectionReason") String rejectionReason);

    /**
     * The symbols worth polling: everything someone holds or has working,
     * minus the instrument classes this venue cannot price.
     */
    @Select("""
            SELECT DISTINCT COALESCE(e.ticker, m.scheme_code) AS symbol
            FROM instrument i
            LEFT JOIN equity      e ON e.instrument_id = i.instrument_id
            LEFT JOIN mutual_fund m ON m.instrument_id = i.instrument_id
            WHERE i.is_tradable
              AND i.instrument_type <> 'MF'
              AND (EXISTS (SELECT 1 FROM position p
                            WHERE p.instrument_id = i.instrument_id)
                OR EXISTS (SELECT 1 FROM orders o
                            WHERE o.instrument_id = i.instrument_id
                              AND o.status = 'NEW'))
            ORDER BY symbol
            """)
    java.util.List<String> findSymbolsWorthPolling();

    /**
     * Moves cash and releases (or charges) blocked funds, conditioned on the
     * version the caller read.
     */
    @Update("""
            UPDATE client_account
               SET balance       = balance + #{balanceDelta},
                   blocked_funds = blocked_funds + #{blockedDelta},
                   version       = version + 1
             WHERE client_id = #{accountId}
               AND version   = #{expectedVersion}
            """)
    int updateAccount(@Param("accountId") Long accountId,
                      @Param("balanceDelta") BigDecimal balanceDelta,
                      @Param("blockedDelta") BigDecimal blockedDelta,
                      @Param("expectedVersion") int expectedVersion);

    @Insert("""
            INSERT INTO position (client_id, instrument_id, position_type, quantity, average_price)
            VALUES (#{accountId}, #{instrumentId}, #{positionType}, #{quantity}, #{averagePrice})
            """)
    void insertPosition(@Param("accountId") Long accountId,
                        @Param("instrumentId") Long instrumentId,
                        @Param("positionType") String positionType,
                        @Param("quantity") BigDecimal quantity,
                        @Param("averagePrice") BigDecimal averagePrice);

    @Update("""
            UPDATE position
               SET quantity      = #{quantity},
                   average_price = #{averagePrice}
             WHERE client_id     = #{accountId}
               AND instrument_id = #{instrumentId}
               AND position_type = #{positionType}
            """)
    void updatePosition(@Param("accountId") Long accountId,
                        @Param("instrumentId") Long instrumentId,
                        @Param("positionType") String positionType,
                        @Param("quantity") BigDecimal quantity,
                        @Param("averagePrice") BigDecimal averagePrice);

    @Delete("""
            DELETE FROM position
             WHERE client_id     = #{accountId}
               AND instrument_id = #{instrumentId}
               AND position_type = #{positionType}
            """)
    void deletePosition(@Param("accountId") Long accountId,
                        @Param("instrumentId") Long instrumentId,
                        @Param("positionType") String positionType);
}
