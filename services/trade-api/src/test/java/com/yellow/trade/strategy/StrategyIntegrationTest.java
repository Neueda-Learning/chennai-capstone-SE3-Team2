package com.yellow.trade.strategy;

import com.yellow.trade.integration.PostgresSupport;
import com.yellow.trade.marketdata.ChartRange;
import com.yellow.trade.marketdata.CandleService;
import com.yellow.trade.marketdata.Candle;
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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.stream.IntStream;
import java.time.ZoneId;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Strategies against the real schema and the real order route. The quote
 * and the outcome are handed in as the listener would hand them; the order
 * goes over HTTP to this service's own POST /api/v1/orders, token filter,
 * validation and idempotency included. Only auth's minting is replaced: it
 * answers with a token for the account, signed as auth signs one.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIf(
        value = "com.yellow.trade.integration.PostgresSupport#databaseAvailable",
        disabledReason = "needs a database: start Docker, or set IT_DB_URL "
                + "(with IT_DB_USER and IT_DB_PASSWORD) to a PostgreSQL you already have")
class StrategyIntegrationTest extends PostgresSupport {

    private static final String BUY_THE_DIP = """
            {"symbol":"ITC.NS","side":"BUY","quantity":2,"trigger":"FALLS_THROUGH",
             "triggerPrice":250.00,"maxSpend":600,"maxPosition":20}""";

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StrategyTrigger trigger;
    @Autowired private StrategyOutcomes outcomes;
    @MockitoBean private StrategyTokenClient tokens;
    /** The daily history an indicator trigger reads; the order route and everything else are real. */
    @MockitoBean private CandleService candles;

    @BeforeEach
    void rebuildDatabase() {
        applySchema(jdbc);
        when(tokens.mint(anyLong())).thenAnswer(call ->
                tokenFor(call.<Long>getArgument(0)).getFirst(HttpHeaders.AUTHORIZATION).substring("Bearer ".length()));
    }

    private ResponseEntity<Map> send(long asAccount, HttpMethod method, String path, String body) {
        return rest.exchange(path, method, new HttpEntity<>(body, tokenFor(asAccount)), Map.class);
    }

    private long create(String body) {
        ResponseEntity<Map> created = send(3, HttpMethod.POST, "/api/v1/accounts/3/strategies", body);
        assertThat(created.getStatusCode(), is(HttpStatus.CREATED));
        return ((Number) created.getBody().get("id")).longValue();
    }

    private void enable(long strategyId) {
        assertThat(send(3, HttpMethod.PUT, "/api/v1/accounts/3/strategies/" + strategyId + "/enabled", "{\"enabled\":true}")
                .getStatusCode(), is(HttpStatus.OK));
    }

    private Map<?, ?> strategy(long strategyId) {
        for (Object one : rest.exchange("/api/v1/accounts/3/strategies", HttpMethod.GET,
                new HttpEntity<>(tokenFor(3)), List.class).getBody()) {
            if (((Number) ((Map<?, ?>) one).get("id")).longValue() == strategyId) {
                return (Map<?, ?>) one;
            }
        }
        throw new AssertionError("no strategy " + strategyId);
    }

    private List<Map<String, Object>> runs(long strategyId) {
        return rest.exchange("/api/v1/accounts/3/strategies/" + strategyId + "/runs", HttpMethod.GET,
                new HttpEntity<>(tokenFor(3)), List.class).getBody();
    }

    private static StrategyQuote quote(String price) {
        BigDecimal at = new BigDecimal(price);
        return new StrategyQuote(UUID.randomUUID(), "ITC.NS", at, at.subtract(new BigDecimal("0.10")),
                at.add(new BigDecimal("0.10")), Instant.now());
    }

    private int strategyOrders() {
        return jdbc.queryForObject("SELECT count(*) FROM orders WHERE idempotency_key LIKE 'strategy-%'", Integer.class);
    }

    private boolean polled() {
        return jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM strat_polled_symbols p JOIN equity e ON e.instrument_id = p.instrument_id
                               WHERE e.ticker = 'ITC.NS')""", Boolean.class);
    }

    private UUID orderOf(long strategyId) {
        return jdbc.queryForObject("SELECT order_id FROM strat_run WHERE strategy_id = ? AND outcome = 'PLACED' "
                + "ORDER BY run_id DESC LIMIT 1", UUID.class, strategyId);
    }

    private static String outcome(String type, UUID eventId, UUID orderId, String executedPrice, String reason) {
        return """
                {"eventId":"%s","eventType":"%s","eventTime":"2026-10-07T05:00:00Z","source":"trade-executor",
                 "schemaVersion":1,"payload":{"orderId":"%s","accountId":3,"symbol":"ITC.NS","side":"BUY","quantity":2,
                 "price":249.10,"executedPrice":%s,"status":"X","reason":%s,"cashDelta":0,
                 "positionQuantityAfter":null,"averageCostAfter":null,"executedOn":"2026-10-07T05:00:00Z"}}"""
                .formatted(eventId, type, orderId, executedPrice, reason == null ? "null" : "\"" + reason + "\"");
    }

    @Test
    @DisplayName("created off; switched on, a quote through its level places one order through the route, and the same quote never places a second")
    void firesThroughTheRoute() {
        long id = create(BUY_THE_DIP);
        assertThat(strategy(id).get("enabled"), is(false));
        trigger.onQuote(quote("249.00"));
        assertThat("off: nothing fires", strategyOrders(), is(0));
        assertThat(polled(), is(false));

        enable(id);
        assertThat("armed: the executor's poller prices it", polled(), is(true));
        trigger.onQuote(quote("251.00"));
        assertThat("above a falls-through level: nothing", strategyOrders(), is(0));

        StrategyQuote crossing = quote("249.00");
        trigger.onQuote(crossing);

        Map<String, Object> order = jdbc.queryForMap(
                "SELECT client_id, side, quantity, price, status, idempotency_key FROM orders WHERE idempotency_key LIKE 'strategy-%'");
        assertThat(order.get("client_id"), is(3));
        assertThat(order.get("side"), is("BUY"));
        assertThat(((BigDecimal) order.get("quantity")).intValue(), is(2));
        // The ask, 249.10, and half a per cent of room, rounded up to the paisa.
        assertThat((BigDecimal) order.get("price"), comparesEqualTo(new BigDecimal("250.35")));
        assertThat(order.get("idempotency_key"), is("strategy-" + id + "-" + crossing.eventId()));
        assertThat(strategy(id).get("status"), is("FIRED"));
        assertThat(strategy(id).get("lastFiredAt"), is(notNullValue()));
        assertThat(polled(), is(false));
        List<Map<String, Object>> runs = runs(id);
        assertThat(runs.size(), is(1));
        assertThat(runs.get(0).get("outcome"), is("PLACED"));
        assertThat(new BigDecimal(runs.get(0).get("quotePrice").toString()), comparesEqualTo(new BigDecimal("249.00")));

        // Armed again, the same quote replayed finds its run and places nothing.
        enable(id);
        trigger.onQuote(crossing);
        assertThat(strategyOrders(), is(1));
        assertThat(runs(id).size(), is(1));
    }

    @Test
    @DisplayName("a moving-average crossover places its order through the Trade API when today's price crosses, and not before")
    void crossoverThroughTheRoute() {
        // To yesterday: 30 days at 100, then 20 at 99; the 20-day (99) under the 50-day (99.6).
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(1);
        List<Candle> history = IntStream.range(0, 50).mapToObj(i -> {
            BigDecimal close = BigDecimal.valueOf(i < 30 ? 100 : 99);
            return new Candle(yesterday.minusDays(49 - i), close, close, close, close, 1000L);
        }).toList();
        when(candles.candles(eq("ITC.NS"), eq(ChartRange.SIX_MONTHS))).thenReturn(history);
        long id = create("""
                {"symbol":"ITC.NS","side":"BUY","quantity":2,"trigger":"MA_CROSSOVER",
                 "maxSpend":600,"maxPosition":20}""");
        assertThat(strategy(id).get("triggerPrice"), is(org.hamcrest.Matchers.nullValue()));
        enable(id);

        // 110 moves the 20-day to 99.55, still under the 50-day's 99.8: no action.
        trigger.onQuote(quote("110.00"));
        assertThat(strategyOrders(), is(0));
        assertThat(runs(id).size(), is(0));

        // 120 lifts it to 100.05, over the 50-day's 100.00: the order goes through the route.
        StrategyQuote crossing = quote("120.00");
        trigger.onQuote(crossing);

        Map<String, Object> order = jdbc.queryForMap(
                "SELECT side, quantity, price, idempotency_key FROM orders WHERE idempotency_key LIKE 'strategy-%'");
        assertThat(order.get("side"), is("BUY"));
        // The ask, 120.10, and half a per cent of room, rounded up to the paisa.
        assertThat((BigDecimal) order.get("price"), comparesEqualTo(new BigDecimal("120.71")));
        assertThat(order.get("idempotency_key"), is("strategy-" + id + "-" + crossing.eventId()));
        assertThat(strategy(id).get("status"), is("FIRED"));
        assertThat(runs(id).get(0).get("reason"), is("The 20-day average (100.05) crossed above the 50-day (100.00)."));
        Map<?, ?> indicator = (Map<?, ?>) strategy(id).get("indicator");
        assertThat(new BigDecimal(indicator.get("shortAverage").toString()), comparesEqualTo(new BigDecimal("100.05")));
    }

    @Test
    @DisplayName("what came of it: a rejection is counted and re-arms, once however often it is replayed; a fill is a FILLED run")
    void outcomes() {
        long id = create(BUY_THE_DIP);
        enable(id);
        trigger.onQuote(quote("249.00"));
        UUID rejectedOrder = orderOf(id);
        String rejection = outcome("ORDER_REJECTED", UUID.randomUUID(), rejectedOrder, "null", "PRICE_NOT_MET");

        outcomes.apply(rejection);
        outcomes.apply(rejection);

        assertThat(strategy(id).get("failures"), is(1));
        assertThat(strategy(id).get("status"), is("ARMED"));
        assertThat(runs(id).get(0).get("outcome"), is("REJECTED"));
        assertThat(runs(id).get(0).get("reason"), is("PRICE_NOT_MET"));

        trigger.onQuote(quote("248.50"));
        UUID filledOrder = orderOf(id);
        outcomes.apply(outcome("ORDER_FILLED", UUID.randomUUID(), filledOrder, "248.60", null));

        assertThat(runs(id).get(0).get("outcome"), is("FILLED"));
        assertThat(new BigDecimal(runs(id).get(0).get("quotePrice").toString()), comparesEqualTo(new BigDecimal("248.60")));
        assertThat(runs(id).size(), is(4));
        assertThat(strategy(id).get("status"), is("FIRED"));
    }

    @Test
    @DisplayName("bounded: a buy costing more than its spend is refused, recorded and stopped, and nothing reaches the route")
    void overItsSpend() {
        long id = create(BUY_THE_DIP.replace("\"maxSpend\":600", "\"maxSpend\":100"));
        enable(id);

        trigger.onQuote(quote("249.00"));

        assertThat(strategyOrders(), is(0));
        assertThat(strategy(id).get("status"), is("STOPPED"));
        assertThat(runs(id).get(0).get("outcome"), is("REFUSED_LIMIT"));
        assertThat(runs(id).get(0).get("reason"), is("It would cost ₹500.70, more than the ₹100.00 allowed a firing."));
    }

    @Test
    @DisplayName("the route refusing is a failure, in the route's own words: a sale of a stock not held, three times, stops it")
    void theRouteRefuses() {
        long id = create("""
                {"symbol":"ITC.NS","side":"SELL","quantity":2,"trigger":"RISES_THROUGH",
                 "triggerPrice":250.00,"maxSpend":600,"maxPosition":20}""");
        enable(id);

        trigger.onQuote(quote("251.00"));
        assertThat(strategy(id).get("failures"), is(1));
        assertThat(strategy(id).get("status"), is("ARMED"));
        assertThat(runs(id).get(0).get("outcome"), is("FAILED"));
        assertThat(runs(id).get(0).get("reason"), is("ORD-409: Insufficient holdings"));

        trigger.onQuote(quote("251.50"));
        trigger.onQuote(quote("252.00"));

        assertThat(strategy(id).get("status"), is("STOPPED"));
        assertThat(runs(id).get(0).get("outcome"), is("STOPPED"));
        trigger.onQuote(quote("253.00"));
        assertThat(runs(id).size(), is(4));
        assertThat(strategyOrders(), is(0));
    }

    @Test
    @DisplayName("the routes: ten an account and the eleventh LIM-409; a fund 422; another customer's 403 and 404; deleted, gone")
    void routes() {
        long first = create(BUY_THE_DIP);
        for (int i = 1; i < 10; i++) {
            create(BUY_THE_DIP);
        }
        ResponseEntity<Map> eleventh = send(3, HttpMethod.POST, "/api/v1/accounts/3/strategies", BUY_THE_DIP);
        assertThat(eleventh.getStatusCode(), is(HttpStatus.CONFLICT));
        assertThat(eleventh.getBody().get("errorCode"), is("LIM-409"));

        assertThat(send(3, HttpMethod.DELETE, "/api/v1/accounts/3/strategies/" + first, null).getStatusCode(),
                is(HttpStatus.NO_CONTENT));
        ResponseEntity<Map> fund = send(3, HttpMethod.POST, "/api/v1/accounts/3/strategies",
                BUY_THE_DIP.replace("ITC.NS", "122639"));
        assertThat(fund.getStatusCode(), is(HttpStatus.UNPROCESSABLE_ENTITY));
        assertThat(fund.getBody().get("errorCode"), is("VAL-422"));

        long second = ((Number) ((Map<?, ?>) rest.exchange("/api/v1/accounts/3/strategies", HttpMethod.GET,
                new HttpEntity<>(tokenFor(3)), List.class).getBody().get(0)).get("id")).longValue();
        assertThat(send(4, HttpMethod.GET, "/api/v1/accounts/3/strategies", null).getBody().get("errorCode"), is("ACC-403"));
        ResponseEntity<Map> notTheirs = send(4, HttpMethod.PUT, "/api/v1/accounts/4/strategies/" + second + "/enabled",
                "{\"enabled\":true}");
        assertThat(notTheirs.getStatusCode(), is(HttpStatus.NOT_FOUND));
        assertThat(notTheirs.getBody().get("errorCode"), is("STR-404"));
        assertThat(send(3, HttpMethod.GET, "/api/v1/accounts/3/strategies/" + first + "/runs", null).getBody().get("errorCode"),
                is("STR-404"));
    }
}
