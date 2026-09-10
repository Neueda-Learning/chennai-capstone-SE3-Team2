package com.yellow.trade.integration;

import com.yellow.trade.dto.AccountResponse;
import com.yellow.trade.dto.BalanceResponse;
import com.yellow.trade.dto.ErrorResponse;
import com.yellow.trade.dto.OrderHistoryEntry;
import com.yellow.trade.dto.OrderResponse;
import com.yellow.trade.dto.PositionResponse;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * The whole stack: HTTP in, controller, service, domain rules, MyBatis, and a
 * real PostgreSQL carrying the real migrations.
 *
 * These are the flows the review asks to see traced end to end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIf(
        value = "com.yellow.trade.integration.PostgresSupport#databaseAvailable",
        disabledReason = "needs a database: start Docker, or set IT_DB_URL "
                + "(with IT_DB_USER and IT_DB_PASSWORD) to a PostgreSQL you already have")
class TradeApiIntegrationTest extends PostgresSupport {

    /** Seeded: ACTIVE, balance 750,000.0000, blocked 220,000.0000, version 7. */
    private static final long ACTIVE_ACCOUNT = 3L;
    private static final long OTHER_ACCOUNT = 4L;

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void rebuildDatabase() {
        applySchema(jdbc);
    }

    private <T> ResponseEntity<T> get(long asAccount, String path, Class<T> type) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(tokenFor(asAccount)), type);
    }

    // ------------------------------------------------------------ reads

    @Test
    @DisplayName("the account read returns the string business reference and the holder's name")
    void accountReadTraversesTheJoin() {
        ResponseEntity<AccountResponse> response =
                get(ACTIVE_ACCOUNT, "/api/v1/accounts/3", AccountResponse.class);

        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        // account_ref from client_account, name from client_profile: the join
        // the mapper does is what makes both available in one response.
        assertThat(response.getBody().accountId(), is("ACC-000003"));
        assertThat(response.getBody().holderName(), is("Rohan Nair"));
        assertThat(response.getBody().status().name(), is("ACTIVE"));
    }

    @Test
    @DisplayName("available funds is balance minus blocked, read from the seeded row")
    void balanceIsDerivedFromTheRow() {
        BalanceResponse balance =
                get(ACTIVE_ACCOUNT, "/api/v1/accounts/3/balance", BalanceResponse.class).getBody();

        assertThat(balance.cashBalance(), comparesEqualTo(new BigDecimal("750000.0000")));
        assertThat(balance.blockedFunds(), comparesEqualTo(new BigDecimal("220000.0000")));
        assertThat(balance.availableFunds(), comparesEqualTo(new BigDecimal("530000.0000")));
        assertThat(balance.currency(), is("INR"));
    }

    @Test
    @DisplayName("holdings carry their position type and survive delisting")
    void positionsCarryTypeAndIncludeDelisted() {
        PositionResponse[] positions =
                get(ACTIVE_ACCOUNT, "/api/v1/accounts/3/positions", PositionResponse[].class).getBody();

        assertThat(positions, arrayWithSize(3));
        // Records expose components, not bean getters, so assert directly.
        assertThat(List.of(positions).stream().allMatch(p -> "DELIVERY".equals(p.positionType())), is(true));
        // MERSTL is is_tradable = FALSE. No new orders, but the holding stays
        // visible -- which is the whole reason delisting is a flag.
        assertThat(List.of(positions).stream().anyMatch(p -> p.symbol().equals("MERSTL")), is(true));
    }

    @Test
    @DisplayName("the status filter is bound as a parameter and narrows the history")
    void orderHistoryFiltersByStatus() {
        OrderHistoryEntry[] all =
                get(ACTIVE_ACCOUNT, "/api/v1/accounts/3/orders", OrderHistoryEntry[].class).getBody();
        OrderHistoryEntry[] working =
                get(ACTIVE_ACCOUNT, "/api/v1/accounts/3/orders?status=NEW", OrderHistoryEntry[].class).getBody();

        assertThat(all.length > working.length, is(true));
        assertThat(List.of(working).stream()
                .allMatch(e -> e.status() == com.yellow.enums.OrderStatus.NEW), is(true));
    }

    @Test
    @DisplayName("an injection attempt in the status filter is refused, not executed")
    void injectionInTheStatusFilterIsRefused() {
        ResponseEntity<ErrorResponse> response = get(ACTIVE_ACCOUNT,
                "/api/v1/accounts/3/orders?status=NEW%27%20OR%20%271%27=%271", ErrorResponse.class);

        // With ${} this would have run. Bound as a parameter, and typed as an
        // enum before that, it never reaches a statement.
        assertThat(response.getStatusCode(), is(HttpStatus.UNPROCESSABLE_ENTITY));
        assertThat(response.getBody().errorCode(), is("VAL-422"));
    }

    // ------------------------------------------------------- authorisation

    @Test
    @DisplayName("a missing token is AUTH-401 before any controller runs")
    void missingTokenIsRefused() {
        ResponseEntity<ErrorResponse> response =
                rest.getForEntity("/api/v1/accounts/3", ErrorResponse.class);

        assertThat(response.getStatusCode(), is(HttpStatus.UNAUTHORIZED));
        assertThat(response.getBody().errorCode(), is("AUTH-401"));
    }

    @Test
    @DisplayName("a token for another account is ACC-403, identical to a suspended one")
    void tokenThatDoesNotReachIsForbidden() {
        ResponseEntity<ErrorResponse> response =
                get(OTHER_ACCOUNT, "/api/v1/accounts/3", ErrorResponse.class);

        assertThat(response.getStatusCode(), is(HttpStatus.FORBIDDEN));
        assertThat(response.getBody().errorCode(), is("ACC-403"));
        assertThat(response.getBody().message(), is("Account not active"));
    }

    @Test
    @DisplayName("an unknown account is ACC-404")
    void unknownAccountIsNotFound() {
        ResponseEntity<ErrorResponse> response =
                get(999999L, "/api/v1/accounts/999999", ErrorResponse.class);

        assertThat(response.getStatusCode(), is(HttpStatus.NOT_FOUND));
        assertThat(response.getBody().errorCode(), is("ACC-404"));
    }

    // -------------------------------------------------------- placement

    private ResponseEntity<String> place(long asAccount, String symbol, String side,
                                         int quantity, String price, String key) {
        String body = """
                {"accountId":%d,"symbol":"%s","side":"%s","quantity":%d,"price":%s,"idempotencyKey":"%s"}
                """.formatted(asAccount, symbol, side, quantity, price, key);
        return rest.exchange("/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(body, tokenFor(asAccount)), String.class);
    }

    @Test
    @DisplayName("one order, traced from the request to the committed row")
    void orderReachesTheDatabase() {
        ResponseEntity<OrderResponse> response = rest.exchange("/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":3,"symbol":"APEX","side":"BUY","quantity":10,
                         "price":1450.00,"idempotencyKey":"integration-key-01"}
                        """, tokenFor(ACTIVE_ACCOUNT)), OrderResponse.class);

        assertThat(response.getStatusCode(), is(HttpStatus.CREATED));
        UUID orderId = response.getBody().orderId();
        assertThat(orderId, is(notNullValue()));

        // The row, with the defaults migration 002 supplies.
        var row = jdbc.queryForMap(
                "SELECT client_id, side, order_type, product_type, quantity, price, status, resolved_at "
                        + "FROM orders WHERE order_id = ?", orderId);
        assertThat(row.get("client_id"), is(3));
        assertThat(row.get("order_type"), is("MARKET"));
        assertThat(row.get("product_type"), is("CNC"));
        assertThat(row.get("status"), is("NEW"));
        assertThat(row.get("resolved_at"), is(nullValue()));

        // Cash committed with it, under the lock: 10 x 1450.00 = 14,500.
        var account = jdbc.queryForMap(
                "SELECT blocked_funds, version FROM client_account WHERE client_id = 3");
        assertThat((BigDecimal) account.get("blocked_funds"), comparesEqualTo(new BigDecimal("234500.0000")));
        assertThat(account.get("version"), is(8));
    }

    @Test
    @DisplayName("a refused order rolls back: no row, no cash moved")
    void refusedOrderLeavesNothingBehind() {
        ResponseEntity<String> response =
                place(ACTIVE_ACCOUNT, "APEX", "BUY", 1000, "9999.00", "integration-key-02");

        assertThat(response.getStatusCode(), is(HttpStatus.BAD_REQUEST));
        assertThat(response.getBody(), org.hamcrest.Matchers.containsString("ORD-400"));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM orders WHERE idempotency_key = ?", Integer.class,
                "integration-key-02"), is(0));
        assertThat(jdbc.queryForObject(
                "SELECT blocked_funds FROM client_account WHERE client_id = 3", BigDecimal.class),
                comparesEqualTo(new BigDecimal("220000.0000")));
    }

    @Test
    @DisplayName("the unique index refuses a reused idempotency key")
    void reusedIdempotencyKeyIsRefused() {
        assertThat(place(ACTIVE_ACCOUNT, "APEX", "BUY", 1, "100.00", "integration-key-03")
                .getStatusCode(), is(HttpStatus.CREATED));

        ResponseEntity<String> replay =
                place(ACTIVE_ACCOUNT, "APEX", "BUY", 1, "100.00", "integration-key-03");

        assertThat(replay.getStatusCode(), is(HttpStatus.CONFLICT));
        assertThat(replay.getBody(), org.hamcrest.Matchers.containsString("ORD-409"));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM orders WHERE idempotency_key = ?", Integer.class,
                "integration-key-03"), is(1));
    }

    @Test
    @DisplayName("a delisted instrument is INS-404 and the body names no symbol")
    void delistedInstrumentIsRefused() {
        ResponseEntity<String> response =
                place(ACTIVE_ACCOUNT, "MERSTL", "BUY", 1, "100.00", "integration-key-04");

        assertThat(response.getStatusCode(), is(HttpStatus.NOT_FOUND));
        assertThat(response.getBody(), org.hamcrest.Matchers.containsString("INS-404"));
        assertThat(response.getBody(), not(org.hamcrest.Matchers.containsString("MERSTL")));
    }

    // ----------------------------------------------------------- cancel

    @Test
    @DisplayName("cancelling releases the cash, and cancelling twice is ORD-409")
    void cancelReleasesCashAndIsNotRepeatable() {
        UUID orderId = rest.exchange("/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":3,"symbol":"APEX","side":"BUY","quantity":10,
                         "price":1450.00,"idempotencyKey":"integration-key-05"}
                        """, tokenFor(ACTIVE_ACCOUNT)), OrderResponse.class).getBody().orderId();

        ResponseEntity<OrderResponse> cancelled = rest.exchange(
                "/api/v1/orders/" + orderId, HttpMethod.DELETE,
                new HttpEntity<>(tokenFor(ACTIVE_ACCOUNT)), OrderResponse.class);

        assertThat(cancelled.getStatusCode(), is(HttpStatus.OK));
        assertThat(cancelled.getBody().status().name(), is("CANCELLED"));
        assertThat(jdbc.queryForObject(
                "SELECT blocked_funds FROM client_account WHERE client_id = 3", BigDecimal.class),
                comparesEqualTo(new BigDecimal("220000.0000")));

        ResponseEntity<ErrorResponse> again = rest.exchange(
                "/api/v1/orders/" + orderId, HttpMethod.DELETE,
                new HttpEntity<>(tokenFor(ACTIVE_ACCOUNT)), ErrorResponse.class);

        assertThat(again.getStatusCode(), is(HttpStatus.CONFLICT));
        assertThat(again.getBody().errorCode(), is("ORD-409"));
    }

    // ------------------------------------------------------ concurrency

    @Test
    @DisplayName("concurrent buys against one account: one wins, and the cash reconciles")
    void concurrentBuysAreSerialisedByTheVersionColumn() throws Exception {
        // Account 4 holds 9,200.75 with 1,200.00 blocked: 8,000.75 available.
        // Eight buys of 5,000 each. Any one fits; together they are 40,000.
        int attempts = 8;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);

        List<Callable<ResponseEntity<String>>> calls = IntStream.range(0, attempts)
                .<Callable<ResponseEntity<String>>>mapToObj(i ->
                        () -> place(OTHER_ACCOUNT, "APEX", "BUY", 5, "1000.00", "race-key-" + i))
                .toList();

        List<Future<ResponseEntity<String>>> results = pool.invokeAll(calls);
        pool.shutdown();

        long created = 0;
        for (Future<ResponseEntity<String>> future : results) {
            if (future.get().getStatusCode() == HttpStatus.CREATED) {
                created++;
            }
        }

        // Without the lock, every one of them would read 8,000.75, every one
        // would pass rule 6, and every one would write its own figure back.
        assertThat(created, is(1L));

        BigDecimal blocked = jdbc.queryForObject(
                "SELECT blocked_funds FROM client_account WHERE client_id = 4", BigDecimal.class);
        BigDecimal committed = jdbc.queryForObject(
                "SELECT coalesce(sum(price * quantity), 0) FROM orders "
                        + "WHERE client_id = 4 AND idempotency_key LIKE 'race-key-%'", BigDecimal.class);

        // The reconciliation somebody would otherwise do the next morning.
        assertThat(blocked, comparesEqualTo(new BigDecimal("1200.0000").add(committed)));
    }
}
