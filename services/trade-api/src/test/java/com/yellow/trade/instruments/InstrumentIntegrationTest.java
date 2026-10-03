package com.yellow.trade.instruments;

import com.yellow.trade.integration.PostgresSupport;
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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIf(
        value = "com.yellow.trade.integration.PostgresSupport#databaseAvailable",
        disabledReason = "needs a database: start Docker, or set IT_DB_URL "
                + "(with IT_DB_USER and IT_DB_PASSWORD) to a PostgreSQL you already have")
class InstrumentIntegrationTest extends PostgresSupport {

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void rebuildDatabase() {
        applySchema(jdbc);
    }

    @Test
    @DisplayName("Lists the seeded tradable instruments by symbol -- stocks, ETFs and funds -- and leaves out the delisted")
    void tradableOnly() {
        ResponseEntity<Map[]> response = rest.exchange("/api/v1/instruments", HttpMethod.GET,
                new HttpEntity<>(tokenFor(3)), Map[].class);

        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        List<String> symbols = Arrays.stream(response.getBody()).map(i -> (String) i.get("symbol")).toList();
        assertThat(symbols, hasItems("ITC.NS", "TCS.NS", "APEX", "NIFTYBEES", "SCH100001"));
        assertThat(symbols, not(hasItem("MERSTL")));
        assertThat(symbols, is(symbols.stream().sorted().toList()));
    }

    @Test
    @DisplayName("Needs a token, like every route under /api/v1/")
    void needsAToken() {
        ResponseEntity<String> response = rest.getForEntity("/api/v1/instruments", String.class);

        assertThat(response.getStatusCode(), is(HttpStatus.UNAUTHORIZED));
    }
}
