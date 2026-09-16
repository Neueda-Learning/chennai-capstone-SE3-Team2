package com.yellow.trade.characterisation;

import com.yellow.trade.dto.ErrorResponse;
import com.yellow.trade.dto.OrderResponse;
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

import java.math.BigDecimal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIf(
        value = "com.yellow.trade.integration.PostgresSupport#databaseAvailable",
        disabledReason = "needs a database: start Docker, or set IT_DB_URL "
                + "(with IT_DB_USER and IT_DB_PASSWORD) to a PostgreSQL you already have")
class OrderPlacementCharacterisationTest extends PostgresSupport {

    private static final long ACTIVE_ACCOUNT = 3L;     // seeded ACTIVE, balance 750,000.0000
    private static final long SUSPENDED_ACCOUNT = 8L;  // seeded SUSPENDED, balance 22,000.0000

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void rebuildDatabase() {
        applySchema(jdbc);
    }

    @Test
    @DisplayName("PINNED: an affordable order fills synchronously and returns FILLED, field by field")
    void affordableOrderFillsSynchronously() {
        ResponseEntity<OrderResponse> response = rest.exchange("/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":3,"symbol":"APEX","side":"BUY","quantity":10,
                         "price":1450.00,"idempotencyKey":"char-key-01"}
                        """, tokenFor(ACTIVE_ACCOUNT)), OrderResponse.class);

        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        OrderResponse body = response.getBody();
        assertThat(body.orderId(), startsWith("ORD-"));
        // PINNED: currently returns the TERMINAL status FILLED, not NEW.
        // Sprint 7's change to NEW updates this line in the SAME commit.
        assertThat(body.status().name(), is("FILLED"));
        assertThat(body.message(), is("Order executed"));
        assertThat(body.symbol(), is("APEX"));
        assertThat(body.side().name(), is("BUY"));
        assertThat(body.quantity(), comparesEqualTo(new BigDecimal("10")));
        assertThat(body.price(), comparesEqualTo(new BigDecimal("1450.00")));
    }

    @Test
    @DisplayName("PINNED: an accepted order writes the order row, debits cash and opens the position")
    void acceptedOrderWritesRowCashAndPosition() {
        rest.exchange("/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":3,"symbol":"APEX","side":"BUY","quantity":10,
                         "price":1450.00,"idempotencyKey":"char-key-02"}
                        """, tokenFor(ACTIVE_ACCOUNT)), OrderResponse.class);

        var row = jdbc.queryForMap(
                "SELECT status FROM orders WHERE idempotency_key = ?", "char-key-02");
        assertThat(row.get("status"), is("FILLED"));

        BigDecimal balance = jdbc.queryForObject(
                "SELECT balance FROM client_account WHERE client_id = 3", BigDecimal.class);
        assertThat(balance, comparesEqualTo(new BigDecimal("735500.0000")));

        BigDecimal positionQty = jdbc.queryForObject(
                "SELECT quantity FROM position WHERE client_id = 3 AND instrument_id = "
                        + "(SELECT instrument_id FROM equity WHERE ticker = 'APEX') "
                        + "AND position_type = 'DELIVERY'", BigDecimal.class);
        assertThat(positionQty, comparesEqualTo(new BigDecimal("10.000000")));
    }

    @Test
    @DisplayName("PINNED: a reused idempotency key is ORD-409 CONFLICT")
    void reusedIdempotencyKeyIsConflict() {
        assertThat(rest.exchange("/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":3,"symbol":"APEX","side":"BUY","quantity":1,
                         "price":100.00,"idempotencyKey":"char-key-03"}
                        """, tokenFor(ACTIVE_ACCOUNT)), OrderResponse.class)
                .getStatusCode(), is(HttpStatus.OK));

        ResponseEntity<ErrorResponse> replay = rest.exchange("/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":3,"symbol":"APEX","side":"BUY","quantity":1,
                         "price":100.00,"idempotencyKey":"char-key-03"}
                        """, tokenFor(ACTIVE_ACCOUNT)), ErrorResponse.class);

        assertThat(replay.getStatusCode(), is(HttpStatus.CONFLICT));
        assertThat(replay.getBody().errorCode(), is("ORD-409"));
    }

    @Test
    @DisplayName("PINNED: an unaffordable buy is ORD-400 BAD_REQUEST and writes nothing")
    void unaffordableBuyIsBadRequest() {
        ResponseEntity<ErrorResponse> response = rest.exchange("/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":3,"symbol":"APEX","side":"BUY","quantity":1000,
                         "price":9999.00,"idempotencyKey":"char-key-04"}
                        """, tokenFor(ACTIVE_ACCOUNT)), ErrorResponse.class);

        assertThat(response.getStatusCode(), is(HttpStatus.BAD_REQUEST));
        assertThat(response.getBody().errorCode(), is("ORD-400"));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM orders WHERE idempotency_key = ?", Integer.class,
                "char-key-04"), is(0));
    }

    @Test
    @DisplayName("PINNED: an unknown symbol is INS-404 NOT_FOUND")
    void unknownSymbolIsNotFound() {
        ResponseEntity<ErrorResponse> response = rest.exchange("/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":3,"symbol":"NOSUCHSYMBOL","side":"BUY","quantity":1,
                         "price":100.00,"idempotencyKey":"char-key-05"}
                        """, tokenFor(ACTIVE_ACCOUNT)), ErrorResponse.class);

        assertThat(response.getStatusCode(), is(HttpStatus.NOT_FOUND));
        assertThat(response.getBody().errorCode(), is("INS-404"));
    }

    @Test
    @DisplayName("PINNED: an order on a SUSPENDED account is ACC-403 FORBIDDEN")
    void suspendedAccountIsForbidden() {
        ResponseEntity<ErrorResponse> response = rest.exchange("/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":8,"symbol":"APEX","side":"BUY","quantity":1,
                         "price":100.00,"idempotencyKey":"char-key-06"}
                        """, tokenFor(SUSPENDED_ACCOUNT)), ErrorResponse.class);

        assertThat(response.getStatusCode(), is(HttpStatus.FORBIDDEN));
        assertThat(response.getBody().errorCode(), is("ACC-403"));
    }
}
