package com.yellow.trade.portfolio;

import com.yellow.trade.integration.PostgresSupport;
import com.yellow.trade.marketdata.PriceQuote;
import com.yellow.trade.marketdata.PriceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Portfolio against the real schema and the real seed: account 3 holds
 * 175 ORIONM, 150 SUMCEM (at 597.50) and 500 MERSTL. Prices come through the price layer,
 * replaced here so a test decides what is priced; everything else is real,
 * the token filter included.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIf(
        value = "com.yellow.trade.integration.PostgresSupport#databaseAvailable",
        disabledReason = "needs a database: start Docker, or set IT_DB_URL "
                + "(with IT_DB_USER and IT_DB_PASSWORD) to a PostgreSQL you already have")
class PortfolioIntegrationTest extends PostgresSupport {

    private static final Instant AS_OF = Instant.parse("2026-10-07T04:00:00Z");

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RealisedBook book;
    @MockitoBean private PriceService prices;

    @BeforeEach
    void rebuildDatabase() {
        applySchema(jdbc);
    }

    /** Every symbol asked for priced at this, unless it is one of the unpriced. */
    private void priceEverythingAt(String price, String... unpriced) {
        org.mockito.Mockito.doAnswer(call -> {
            Map<String, PriceQuote> out = new HashMap<>();
            for (Object symbol : (Collection<?>) call.getArgument(0)) {
                if (!List.of(unpriced).contains(symbol)) {
                    out.put((String) symbol, new PriceQuote((String) symbol, new BigDecimal(price), null, null, null,
                            null, null, "INR", AS_OF, false));
                }
            }
            return out;
        }).when(prices).latest(any());
    }

    private ResponseEntity<Map> get(long asAccount, String path) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(tokenFor(asAccount)), Map.class);
    }

    private ResponseEntity<List> list(long asAccount, String path) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(tokenFor(asAccount)), List.class);
    }

    /** A real sell of SUMCEM, placed through the route and settled FILLED as the executor settles one. */
    private UUID filledSaleOfSumcem(String quantity, String fillPrice) {
        HttpHeaders headers = tokenFor(3);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String displayed = (String) rest.exchange("/api/v1/orders", HttpMethod.POST, new HttpEntity<>("""
                {"accountId":3,"symbol":"SUMCEM","side":"SELL","quantity":%s,"price":1.00,
                 "idempotencyKey":"%s"}""".formatted(quantity, UUID.randomUUID()), headers), Map.class)
                .getBody().get("orderId");
        UUID orderId = UUID.fromString(displayed.substring("ORD-".length()));
        jdbc.update("UPDATE orders SET status = 'FILLED', fill_price = ?, resolved_at = now() WHERE order_id = ?",
                new BigDecimal(fillPrice), orderId);
        return orderId;
    }

    private static String saleEvent(UUID eventId, UUID orderId, String quantity, String price, String averageCost) {
        return """
                {"eventId":"%s","eventType":"ORDER_FILLED","eventTime":"2026-10-07T04:00:00Z","source":"trade-executor",
                 "schemaVersion":1,"payload":{"orderId":"%s","accountId":3,"symbol":"SUMCEM","side":"SELL",
                 "quantity":%s,"price":1.00,"executedPrice":%s,"status":"FILLED","reason":null,"cashDelta":0,
                 "positionQuantityAfter":6,"averageCostAfter":%s,"executedOn":"2026-10-07T04:00:01Z"}}"""
                .formatted(eventId, orderId, quantity, price, averageCost);
    }

    private String tradingTablesFingerprint() {
        return jdbc.queryForObject("""
                SELECT md5((SELECT string_agg(t::text, '|' ORDER BY t::text) FROM position t)
                        || (SELECT string_agg(t::text, '|' ORDER BY t::text) FROM client_account t)
                        || coalesce((SELECT string_agg(t::text, '|' ORDER BY t::text) FROM orders t), ''))""",
                String.class);
    }

    @Test
    @DisplayName("priced from live quotes: every holding valued, the total is cash plus market value")
    void priced() {
        priceEverythingAt("100");

        Map<?, ?> summary = get(3, "/api/v1/portfolio/3").getBody();

        // 175 + 150 + 500 units at 100.
        assertThat(new BigDecimal(summary.get("marketValue").toString()), comparesEqualTo(new BigDecimal("82500.00")));
        assertThat(summary.get("positionCount"), is(3));
        assertThat(summary.get("partial"), is(false));
        assertThat(summary.get("baseCurrency"), is("INR"));
        BigDecimal cash = new BigDecimal(summary.get("cashBalance").toString());
        BigDecimal available = jdbc.queryForObject(
                "SELECT balance - blocked_funds FROM client_account WHERE client_id = 3", BigDecimal.class);
        assertThat(cash, comparesEqualTo(available.setScale(2, java.math.RoundingMode.HALF_UP)));
        assertThat(new BigDecimal(summary.get("totalValue").toString()), comparesEqualTo(cash.add(new BigDecimal("82500"))));
    }

    @Test
    @DisplayName("nothing priced is 503 MKT-503; some priced is 200, partial, the others stale with no price")
    void partialAndUnavailable() {
        priceEverythingAt("100", "ORIONM", "SUMCEM", "MERSTL");
        ResponseEntity<Map> none = get(3, "/api/v1/portfolio/3");
        assertThat(none.getStatusCode(), is(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(none.getBody().get("errorCode"), is("MKT-503"));

        priceEverythingAt("100", "MERSTL");
        assertThat(get(3, "/api/v1/portfolio/3").getBody().get("partial"), is(true));
        Map<?, ?> merstl = null;
        for (Object position : list(3, "/api/v1/portfolio/3/positions").getBody()) {
            if ("MERSTL".equals(((Map<?, ?>) position).get("symbol"))) {
                merstl = (Map<?, ?>) position;
            }
        }
        assertThat(merstl.get("lastPrice"), is(nullValue()));
        assertThat(merstl.get("stale"), is(true));
        assertThat(new BigDecimal(merstl.get("costBasis").toString()), comparesEqualTo(new BigDecimal("42150.00")));
        // Asked about that one alone, and it cannot be priced: nothing asked for is priced, 503.
        assertThat(get(3, "/api/v1/portfolio/3/positions?symbol=MERSTL").getStatusCode(), is(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    @DisplayName("realised is booked at the sale and never moves with today's price")
    void realisedAtTheSale() {
        UUID order = filledSaleOfSumcem("4", "610.00");
        book.book(saleEvent(UUID.randomUUID(), order, "4", "610.00", "597.5000"));

        priceEverythingAt("100");
        Map<?, ?> before = get(3, "/api/v1/portfolio/3/pnl").getBody();
        priceEverythingAt("900");
        Map<?, ?> after = get(3, "/api/v1/portfolio/3/pnl").getBody();

        // (610.00 - 597.50) * 4
        assertThat(new BigDecimal(before.get("realisedPnl").toString()), comparesEqualTo(new BigDecimal("50.00")));
        assertThat(new BigDecimal(after.get("realisedPnl").toString()), comparesEqualTo(new BigDecimal("50.00")));
        assertThat(new BigDecimal(get(3, "/api/v1/portfolio/3").getBody().get("realisedPnl").toString()),
                comparesEqualTo(new BigDecimal("50.00")));
    }

    @Test
    @DisplayName("a replayed sale does not double-count, and a sale orders never recorded is not booked")
    void replayAndForgery() {
        UUID order = filledSaleOfSumcem("4", "610.00");
        String event = saleEvent(UUID.randomUUID(), order, "4", "610.00", "597.5000");

        book.book(event);
        book.book(event);
        book.book(saleEvent(UUID.randomUUID(), UUID.randomUUID(), "4", "999", "1"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM pf_realised", Integer.class), is(1));
    }

    @Test
    @DisplayName("from and to bound the realised figure, by Indian date; unrealised is always now")
    void range() {
        UUID order = filledSaleOfSumcem("4", "610.00");
        book.book(saleEvent(UUID.randomUUID(), order, "4", "610.00", "597.5000"));
        priceEverythingAt("100");

        Map<?, ?> thatDay = get(3, "/api/v1/portfolio/3/pnl?from=2026-10-07&to=2026-10-07&bySymbol=true").getBody();
        Map<?, ?> dayBefore = get(3, "/api/v1/portfolio/3/pnl?from=2026-10-06&to=2026-10-06").getBody();

        assertThat(new BigDecimal(thatDay.get("realisedPnl").toString()), comparesEqualTo(new BigDecimal("50.00")));
        assertThat(new BigDecimal(dayBefore.get("realisedPnl").toString()), comparesEqualTo(BigDecimal.ZERO));
        assertThat(dayBefore.get("unrealisedPnl"), is(thatDay.get("unrealisedPnl")));
        assertThat(dayBefore.containsKey("bySymbol"), is(false));
        assertThat(((List<?>) thatDay.get("bySymbol")).size(), is(3));
    }

    @Test
    @DisplayName("another account's portfolio is 403 ACC-403, never 404")
    void anotherAccount() {
        priceEverythingAt("100");
        for (String path : new String[] {"/api/v1/portfolio/3", "/api/v1/portfolio/3/positions", "/api/v1/portfolio/3/pnl"}) {
            ResponseEntity<Map> refused = get(4, path);
            assertThat(refused.getStatusCode(), is(HttpStatus.FORBIDDEN));
            assertThat(refused.getBody().get("errorCode"), is("ACC-403"));
        }
        assertThat(get(4, "/api/v1/portfolio/987654").getBody().get("errorCode"), is("ACC-403"));
    }

    @Test
    @DisplayName("health answers without a token, status only")
    void healthIsPublic() {
        ResponseEntity<Map> health = rest.getForEntity("/health", Map.class);

        assertThat(health.getStatusCode(), is(HttpStatus.OK));
        assertThat(((List<?>) health.getBody().get("dependencies")).size(), is(2));
    }

    @Test
    @DisplayName("read-only means read-only: the routes change nothing in the trading tables")
    void readOnly() {
        priceEverythingAt("100", "MERSTL");
        String before = tradingTablesFingerprint();

        get(3, "/api/v1/portfolio/3");
        list(3, "/api/v1/portfolio/3/positions");
        get(3, "/api/v1/portfolio/3/pnl?bySymbol=true");

        assertThat(tradingTablesFingerprint(), is(before));
    }
}
