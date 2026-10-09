package com.yellow.executor.settle;

import com.yellow.executor.persistence.ExecutionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

/**
 * Sprint 10: the poller also prices what an armed strategy waits on, read
 * through the strategy module's view strat_polled_symbols, so a strategy on a
 * stock nobody holds still sees the quote that fires it.
 */
@SpringBootTest
@EnabledIf(
        value = "com.yellow.executor.settle.ExecutorPostgresSupport#databaseAvailable",
        disabledReason = "needs a database: start Docker, or set IT_DB_URL "
                + "(with IT_DB_USER and IT_DB_PASSWORD) to a PostgreSQL you already have")
class StrategyPolledSymbolsIntegrationTest extends ExecutorPostgresSupport {

    @Autowired private ExecutionMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    /** A tradable stock nobody holds and nobody has an order working on. */
    private String unheld;
    private long unheldId;

    @BeforeEach
    void rebuild() {
        applySchema(jdbc);
        var row = jdbc.queryForMap("""
                SELECT i.instrument_id, e.ticker
                FROM instrument i JOIN equity e ON e.instrument_id = i.instrument_id
                WHERE i.is_tradable AND i.instrument_type = 'STOCK'
                  AND NOT EXISTS (SELECT 1 FROM position p WHERE p.instrument_id = i.instrument_id)
                  AND NOT EXISTS (SELECT 1 FROM orders o WHERE o.instrument_id = i.instrument_id AND o.status = 'NEW')
                ORDER BY i.instrument_id
                LIMIT 1""");
        unheldId = ((Number) row.get("instrument_id")).longValue();
        unheld = (String) row.get("ticker");
    }

    private long strategy(boolean enabled) {
        return jdbc.queryForObject("""
                INSERT INTO strat_strategy (client_id, instrument_id, side, quantity, trigger_kind, trigger_price,
                                            max_spend, max_position, enabled)
                VALUES (3, ?, 'BUY', 1, 'FALLS_THROUGH', 100, 200, 5, ?) RETURNING strategy_id""",
                Long.class, unheldId, enabled);
    }

    @Test
    @DisplayName("a stock with an enabled, armed strategy is polled; switched off, it is not")
    void armed() {
        long off = strategy(false);
        assertThat(mapper.findSymbolsWorthPolling(), not(hasItem(unheld)));

        jdbc.update("UPDATE strat_strategy SET enabled = true WHERE strategy_id = ?", off);
        assertThat(mapper.findSymbolsWorthPolling(), hasItem(unheld));

        jdbc.update("UPDATE strat_strategy SET enabled = false WHERE strategy_id = ?", off);
        assertThat(mapper.findSymbolsWorthPolling(), not(hasItem(unheld)));
    }

    @Test
    @DisplayName("once it has fired or stopped, nothing is waiting on the stock, and it is not polled")
    void firedOrStopped() {
        long armed = strategy(true);
        assertThat(mapper.findSymbolsWorthPolling(), hasItem(unheld));

        jdbc.update("UPDATE strat_strategy SET status = 'FIRED' WHERE strategy_id = ?", armed);
        assertThat(mapper.findSymbolsWorthPolling(), not(hasItem(unheld)));

        jdbc.update("UPDATE strat_strategy SET status = 'STOPPED' WHERE strategy_id = ?", armed);
        assertThat(mapper.findSymbolsWorthPolling(), not(hasItem(unheld)));
    }
}
