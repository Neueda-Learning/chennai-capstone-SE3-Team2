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

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

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
    @DisplayName("A search puts the exact symbol first")
    void searchExactSymbolFirst() {
        List<Map> found = get("/api/v1/instruments?q=itc");

        assertThat(found.get(0).get("symbol"), is("ITC.NS"));
    }

    @Test
    @DisplayName("A search ranks a name starting with the text above a name merely containing it")
    void searchRanksNamePrefix() {
        List<String> symbols = get("/api/v1/instruments?q=bluechip").stream()
                .map(i -> (String) i.get("symbol")).toList();

        // "Bluechip Growth Fund" starts with it; the ICICI fund's name only
        // mentions it in brackets.
        assertThat(symbols, is(List.of("SCH100001", "120586")));
    }

    @Test
    @DisplayName("A search leaves the delisted out, and treats % and _ as text, not wildcards")
    void searchLeavesOutDelistedAndWildcards() {
        assertThat(get("/api/v1/instruments?q=meridian"), is(empty()));
        assertThat(get("/api/v1/instruments?q=%25"), is(empty()));
        assertThat(get("/api/v1/instruments?q=_"), is(empty()));
    }

    @Test
    @DisplayName("A search narrowed to funds returns funds only, at most the limit")
    void searchByTypeAndLimit() {
        List<Map> found = get("/api/v1/instruments?q=fund&type=MF&limit=2");

        assertThat(found.size(), is(2));
        assertThat(found.stream().map(i -> i.get("type")).distinct().toList(), is(List.of("MF")));
    }

    @Test
    @DisplayName("A lookup by symbols includes a delisted holding, marked not tradable")
    void lookupIncludesDelisted() {
        List<Map> found = get("/api/v1/instruments?symbols=MERSTL,ITC.NS,NOPE");

        assertThat(found.stream().map(i -> i.get("symbol")).toList(), is(List.of("ITC.NS", "MERSTL")));
        assertThat(found.get(1).get("tradable"), is(false));
    }

    /** The path is sent exactly as written: %25 must reach the server as a percent sign. */
    private List<Map> get(String path) {
        ResponseEntity<Map[]> response = rest.exchange(URI.create(rest.getRootUri() + path), HttpMethod.GET,
                new HttpEntity<>(tokenFor(3)), Map[].class);
        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        return Arrays.asList(response.getBody());
    }

    @Test
    @DisplayName("Needs a token, like every route under /api/v1/")
    void needsAToken() {
        ResponseEntity<String> response = rest.getForEntity("/api/v1/instruments", String.class);

        assertThat(response.getStatusCode(), is(HttpStatus.UNAUTHORIZED));
    }
}
