package com.yellow.trade.advice;

import com.yellow.trade.integration.PostgresSupport;
import com.yellow.trade.marketdata.Candle;
import com.yellow.trade.marketdata.CandleService;
import com.yellow.trade.marketdata.ChartRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * The advice route through the real token filter and the real instrument
 * table; the candles are replaced, so a test decides the history.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIf(
        value = "com.yellow.trade.integration.PostgresSupport#databaseAvailable",
        disabledReason = "needs a database: start Docker, or set IT_DB_URL "
                + "(with IT_DB_USER and IT_DB_PASSWORD) to a PostgreSQL you already have")
class AdviceIntegrationTest extends PostgresSupport {

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private CandleService candles;

    @BeforeEach
    void rebuildDatabase() {
        applySchema(jdbc);
    }

    private ResponseEntity<Map> get(String symbol) {
        return rest.exchange("/api/v1/advice/" + symbol, HttpMethod.GET, new HttpEntity<>(tokenFor(3)), Map.class);
    }

    @Test
    @DisplayName("any signed-in customer reads a listed stock's signal; there is no account in the path")
    void signal() {
        LocalDate last = LocalDate.parse("2026-10-06");
        List<Candle> uptrend = IntStream.range(0, 80).mapToObj(i -> {
            BigDecimal close = BigDecimal.valueOf(100 + 0.4 * i + 4 * Math.sin(i * 1.3));
            return new Candle(last.minusDays(79 - i), close, close, close, close, 1000L);
        }).toList();
        when(candles.candles(eq("APEX"), eq(ChartRange.SIX_MONTHS))).thenReturn(uptrend);

        ResponseEntity<Map> signal = get("APEX");

        assertThat(signal.getStatusCode(), is(HttpStatus.OK));
        assertThat(signal.getBody().get("direction"), is("BUY"));
        assertThat(signal.getBody().get("disclaimer").toString().startsWith("Information, not advice"), is(true));
    }

    @Test
    @DisplayName("without a token it is 401: signals are for signed-in customers")
    void needsAToken() {
        assertThat(rest.getForEntity("/api/v1/advice/APEX", Map.class).getStatusCode(), is(HttpStatus.UNAUTHORIZED));
    }

    @Test
    @DisplayName("a fund is 422; a symbol nobody lists, 404")
    void refused() {
        String fund = jdbc.queryForObject("SELECT scheme_code FROM mutual_fund ORDER BY instrument_id LIMIT 1", String.class);

        assertThat(get(fund).getBody().get("errorCode"), is("VAL-422"));
        assertThat(get("NOPE.NS").getBody().get("errorCode"), is("INS-404"));
    }
}
