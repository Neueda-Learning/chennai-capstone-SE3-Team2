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
 * Sprint 10: the poller also prices what customers only watch, read through
 * the watchlists module's view watch_polled_symbols (decision log 0009), so an
 * alert on a stock nobody holds still sees quotes.
 */
@SpringBootTest
@EnabledIf(
        value = "com.yellow.executor.settle.ExecutorPostgresSupport#databaseAvailable",
        disabledReason = "needs a database: start Docker, or set IT_DB_URL "
                + "(with IT_DB_USER and IT_DB_PASSWORD) to a PostgreSQL you already have")
class PolledSymbolsIntegrationTest extends ExecutorPostgresSupport {

    @Autowired private ExecutionMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    /** A tradable stock nobody holds and nobody has an order working on. */
    private String unwatched;
    private long unwatchedId;

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
        unwatchedId = ((Number) row.get("instrument_id")).longValue();
        unwatched = (String) row.get("ticker");
    }

    private long watchlist() {
        return jdbc.queryForObject(
                "INSERT INTO watch_list (client_id, name, position) VALUES (3, 'Watching', 1) RETURNING watchlist_id",
                Long.class);
    }

    @Test
    @DisplayName("a stock on somebody's watchlist is polled, and stops being polled when it comes off")
    void watched() {
        assertThat(mapper.findSymbolsWorthPolling(), not(hasItem(unwatched)));

        long list = watchlist();
        jdbc.update("INSERT INTO watch_item (watchlist_id, instrument_id, position) VALUES (?, ?, 1)", list, unwatchedId);
        assertThat(mapper.findSymbolsWorthPolling(), hasItem(unwatched));

        jdbc.update("DELETE FROM watch_item WHERE watchlist_id = ?", list);
        assertThat(mapper.findSymbolsWorthPolling(), not(hasItem(unwatched)));
    }

    @Test
    @DisplayName("a stock with an ACTIVE alert is polled; once the alert is cancelled, it is not")
    void alerted() {
        long alert = jdbc.queryForObject("""
                INSERT INTO watch_alert (client_id, instrument_id, direction, threshold)
                VALUES (3, ?, 'ABOVE', 100) RETURNING alert_id""", Long.class, unwatchedId);
        assertThat(mapper.findSymbolsWorthPolling(), hasItem(unwatched));

        jdbc.update("UPDATE watch_alert SET status = 'CANCELLED' WHERE alert_id = ?", alert);
        assertThat(mapper.findSymbolsWorthPolling(), not(hasItem(unwatched)));
    }

    @Test
    @DisplayName("a watched fund is still not polled: this venue cannot price one")
    void fundsStayOut() {
        var fund = jdbc.queryForMap("""
                SELECT i.instrument_id, m.scheme_code FROM instrument i
                JOIN mutual_fund m ON m.instrument_id = i.instrument_id
                ORDER BY i.instrument_id LIMIT 1""");
        jdbc.update("INSERT INTO watch_item (watchlist_id, instrument_id, position) VALUES (?, ?, 1)",
                watchlist(), ((Number) fund.get("instrument_id")).longValue());

        assertThat(mapper.findSymbolsWorthPolling(), not(hasItem((String) fund.get("scheme_code"))));
    }
}
