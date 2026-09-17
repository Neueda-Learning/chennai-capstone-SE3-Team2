package com.yellow.executor.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Everything the executor reads and the one thing story 610 writes.
 *
 * <p>Separate mappers from the Trade REST API's, even though some statements
 * look alike. They are different services reading the same database, and the
 * executor's needs are not the API's: it loads one order by id to decide about
 * it, where the API lists orders for a screen. Sharing a mapper interface would
 * couple two deployables so that a column the API stopped selecting would break
 * settlement.
 */
@Mapper
public interface ExecutionMapper {

    /**
     * The order, with the symbol and the instrument facts the checks need,
     * in one round trip.
     *
     * <p>The symbol lives on whichever subtype the instrument has --
     * equity.ticker for a stock or ETF, mutual_fund.scheme_code for a fund --
     * so both joins are outer and COALESCE picks the one that exists. It is the
     * same shape the API uses, and it is the string that goes to Fauxnance.
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
     *
     * <p>It answers "has this order already been executed" by writing rather
     * than by reading. A read, then a decision, then a write can be run twice
     * by two deliveries arriving together, because both reads see NEW before
     * either write lands. A write conditioned on the state it expects cannot,
     * because the database serialises the two updates: the first turns the row
     * to a terminal status, and the second matches zero rows.
     *
     * <p>Zero rows affected therefore means "another delivery got here first",
     * which is a normal outcome and not an error. The caller publishes nothing
     * and acknowledges.
     *
     * <p>Status and resolved_at are set together because
     * ck_orders_resolved_at_matches_status requires it: a row that is not NEW
     * must carry a resolution timestamp. Setting one without the other is
     * refused by the database rather than producing a half-resolved order.
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
     * The symbols worth polling: everything someone holds or has working, minus
     * the instrument classes this venue cannot price.
     *
     * <p>Mutual funds are excluded in the query rather than filtered afterwards,
     * because every one of them would spend a request on a guaranteed 404 --
     * Fauxnance's registry has no funds in it.
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
}
