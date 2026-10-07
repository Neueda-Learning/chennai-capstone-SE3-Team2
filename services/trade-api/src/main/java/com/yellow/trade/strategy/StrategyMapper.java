package com.yellow.trade.strategy;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** strat_strategy and strat_run: this module's tables, and only this module's. */
@Mapper
public interface StrategyMapper {

    String COLUMNS = """
            SELECT s.strategy_id, s.client_id, s.instrument_id, COALESCE(e.ticker, m.scheme_code) AS symbol,
                   s.side, s.quantity, s.trigger_kind, s.trigger_price, s.max_spend, s.max_position,
                   s.enabled, s.status, s.failures, s.created_at, s.last_fired_at
            FROM strat_strategy s
            JOIN instrument i       ON i.instrument_id = s.instrument_id
            LEFT JOIN equity e      ON e.instrument_id = i.instrument_id
            LEFT JOIN mutual_fund m ON m.instrument_id = i.instrument_id
            """;

    /** One account's changes, one at a time, for the length of the caller's transaction: the cap holds. */
    @Select("SELECT 1 FROM (SELECT pg_advisory_xact_lock(1015, CAST(#{clientId} AS integer))) locked")
    Integer lockAccount(@Param("clientId") long clientId);

    @Select(COLUMNS + " WHERE s.client_id = #{clientId} ORDER BY s.created_at DESC, s.strategy_id DESC")
    List<StrategyRow> findForClient(@Param("clientId") long clientId);

    /** This account's strategy, or null: another account's is not found, the same as one that does not exist. */
    @Select(COLUMNS + " WHERE s.strategy_id = #{strategyId} AND s.client_id = #{clientId}")
    StrategyRow findOwned(@Param("clientId") long clientId, @Param("strategyId") long strategyId);

    @Select("SELECT count(*) FROM strat_strategy WHERE client_id = #{clientId}")
    int countForClient(@Param("clientId") long clientId);

    @Insert("""
            INSERT INTO strat_strategy (client_id, instrument_id, side, quantity, trigger_kind, trigger_price,
                                        max_spend, max_position, created_at)
            VALUES (#{clientId}, #{instrumentId}, #{side}, #{quantity}, #{triggerKind}, #{triggerPrice},
                    #{maxSpend}, #{maxPosition}, #{createdAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "strategyId", keyColumn = "strategy_id")
    int insert(StrategyRow row);

    /** Off at once: it waits for a firing that holds the row, and the next one sees it off. */
    @Update("UPDATE strat_strategy SET enabled = false WHERE strategy_id = #{strategyId} AND client_id = #{clientId}")
    int disable(@Param("clientId") long clientId, @Param("strategyId") long strategyId);

    /** On, and armed again with no failures if it had fired or stopped. */
    @Update("""
            UPDATE strat_strategy
               SET enabled = true,
                   failures = CASE WHEN status = 'ARMED' THEN failures ELSE 0 END,
                   status = 'ARMED'
             WHERE strategy_id = #{strategyId} AND client_id = #{clientId}
            """)
    int enable(@Param("clientId") long clientId, @Param("strategyId") long strategyId);

    @Delete("DELETE FROM strat_strategy WHERE strategy_id = #{strategyId} AND client_id = #{clientId}")
    int delete(@Param("clientId") long clientId, @Param("strategyId") long strategyId);

    /** The strategies a quote at this price could fire: enabled, armed, at or past their level. */
    @Select("""
            SELECT strategy_id FROM strat_strategy
            WHERE instrument_id = #{instrumentId} AND enabled AND status = 'ARMED'
              AND ((trigger_kind = 'FALLS_THROUGH' AND trigger_price >= #{price})
                OR (trigger_kind = 'RISES_THROUGH' AND trigger_price <= #{price}))
            ORDER BY strategy_id
            """)
    List<Long> findCrossed(@Param("instrumentId") long instrumentId, @Param("price") BigDecimal price);

    /**
     * The strategy, locked, only if it may still fire: enabled and armed. The
     * check that disabling stops it at once is this, in the firing transaction.
     */
    @Select(COLUMNS + " WHERE s.strategy_id = #{strategyId} AND s.enabled AND s.status = 'ARMED' FOR UPDATE OF s")
    StrategyRow lockFireable(@Param("strategyId") long strategyId);

    @Update("UPDATE strat_strategy SET status = 'FIRED', last_fired_at = #{at} WHERE strategy_id = #{strategyId}")
    int markFired(@Param("strategyId") long strategyId, @Param("at") Instant at);

    @Update("UPDATE strat_strategy SET status = 'STOPPED' WHERE strategy_id = #{strategyId}")
    int stop(@Param("strategyId") long strategyId);

    /**
     * One more failure; armed again to try at the next quote, or STOPPED at
     * the third. @return failures now, or null if the strategy has just been deleted
     */
    @Select("""
            UPDATE strat_strategy
               SET failures = LEAST(failures + 1, 3),
                   status = CASE WHEN failures + 1 >= 3 THEN 'STOPPED' ELSE 'ARMED' END
             WHERE strategy_id = #{strategyId}
            RETURNING failures
            """)
    // An UPDATE: never answered from the session's cache of selects.
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    Integer recordFailure(@Param("strategyId") long strategyId);

    /** Records a run. The same quote or event for the same strategy again: 0 rows, a no-op. */
    @Insert("""
            INSERT INTO strat_run (strategy_id, at, quote_price, outcome, reason, order_id, source_event_id)
            VALUES (#{strategyId}, #{at}, #{quotePrice}, #{outcome}, #{reason}, #{orderId}, #{sourceEventId})
            ON CONFLICT ON CONSTRAINT uq_strat_run_source DO NOTHING
            """)
    @Options(useGeneratedKeys = true, keyProperty = "runId", keyColumn = "run_id")
    int insertRun(RunRow row);

    /** The firing's run, once the route has answered: the order it placed. */
    @Update("UPDATE strat_run SET order_id = #{orderId} WHERE run_id = #{runId}")
    int markPlaced(@Param("runId") Long runId, @Param("orderId") UUID orderId);

    /** The firing's run, once the route has refused: FAILED, and why. */
    @Update("UPDATE strat_run SET outcome = 'FAILED', reason = #{reason} WHERE run_id = #{runId}")
    int markFailed(@Param("runId") Long runId, @Param("reason") String reason);

    @Select("""
            SELECT run_id, strategy_id, at, quote_price, outcome, reason, order_id, source_event_id
            FROM strat_run WHERE strategy_id = #{strategyId} ORDER BY at DESC, run_id DESC
            """)
    List<RunRow> findRuns(@Param("strategyId") long strategyId);

    /** The strategy whose firing placed this order, or null: not a strategy's order. */
    @Select("SELECT strategy_id FROM strat_run WHERE order_id = #{orderId} AND outcome = 'PLACED' LIMIT 1")
    Long findByPlacedOrder(@Param("orderId") UUID orderId);
}
