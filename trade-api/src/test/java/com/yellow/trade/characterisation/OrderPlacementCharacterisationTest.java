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
import static org.hamcrest.Matchers.nullValue;
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
    @DisplayName("PINNED: an affordable order is accepted at NEW and executor fills it later")
    void affordableOrderIsAcceptedAtNew() {
        ResponseEntity<OrderResponse> response = rest.exchange("/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":3,"symbol":"APEX","side":"BUY","quantity":10,
                         "price":1450.00,"idempotencyKey":"char-key-01"}
                        """, tokenFor(ACTIVE_ACCOUNT)), OrderResponse.class);

        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        OrderResponse body = response.getBody();
        assertThat(body.orderId(), startsWith("ORD-"));
        // Sprint 7: order is accepted at NEW status, executor fills later
        assertThat(body.status().name(), is("NEW"));
        assertThat(body.message(), is("Order accepted"));
        assertThat(body.symbol(), is("APEX"));
        assertThat(body.side().name(), is("BUY"));
        assertThat(body.quantity(), comparesEqualTo(new BigDecimal("10")));
        assertThat(body.price(), comparesEqualTo(new BigDecimal("1450.00")));
    }

    @Test
    @DisplayName("PINNED: an accepted order writes the order row at NEW, and moves neither cash nor position")
    void acceptedOrderWritesRowCashAndPosition() {
        rest.exchange("/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":3,"symbol":"APEX","side":"BUY","quantity":10,
                         "price":1450.00,"idempotencyKey":"char-key-02"}
                        """, tokenFor(ACTIVE_ACCOUNT)), OrderResponse.class);

        // DELIBERATELY REPINNED IN SPRINT 7. This test recorded Sprint 6's
        // behaviour: the order was written FILLED, 14,500 was debited and the
        // position was opened, all inside the request. Sprint 7 splits accepting
        // an order from executing it, so placement now records the order and
        // nothing else. Cash and position move in the Trade Executor's
        // settlement transaction, at the executed price rather than the limit
        // price, which is the only price that was ever real.
        var row = jdbc.queryForMap(
                "SELECT status, fill_price, resolved_at FROM orders WHERE idempotency_key = ?",
                "char-key-02");
        assertThat(row.get("status"), is("NEW"));
        // A NEW order has not resolved and has not filled. Both are enforced by
        // ck_orders_resolved_at_matches_status and ck_orders_fill_price_matches_status.
        assertThat(row.get("fill_price"), is(nullValue()));
        assertThat(row.get("resolved_at"), is(nullValue()));

        // REPINNED AGAIN IN SPRINT 7, second change, and for a different reason
        // from the first. The first repin recorded that placement stopped
        // filling: no debit, no position. That still holds -- the balance below
        // is untouched.
        //
        // What changed now is that placement RESERVES. Removing the synchronous
        // debit left nothing decrementing the funds an accepted order commits,
        // so every order in flight was assessed against money another order had
        // already spoken for. blocked_funds is that reservation, balance is
        // untouched because the money is still the customer's, and
        // availableFunds() -- balance minus blocked -- is what rule 6 reads.
        var account = jdbc.queryForMap(
                "SELECT balance, blocked_funds, version FROM client_account WHERE client_id = 3");

        // The cash itself has NOT moved. Only the executor debits.
        assertThat((BigDecimal) account.get("balance"), comparesEqualTo(new BigDecimal("750000.0000")));

        // 10 x 1450.00 = 14,500 reserved at the LIMIT price, on top of the
        // 220,000 the seed already holds. The limit, not a fill price: this
        // order has not been priced and 14,500 is the most it can cost.
        assertThat((BigDecimal) account.get("blocked_funds"),
                comparesEqualTo(new BigDecimal("234500.0000")));

        // And the version turned, because the reservation is a write to the
        // account row under the same optimistic lock as every other.
        assertThat(account.get("version"), is(8));

        // No holding either. The customer owns nothing until the order executes.
        Integer positions = jdbc.queryForObject(
                "SELECT count(*) FROM position WHERE client_id = 3 AND instrument_id = "
                        + "(SELECT instrument_id FROM equity WHERE ticker = 'APEX') "
                        + "AND position_type = 'DELIVERY'", Integer.class);
        assertThat(positions, is(0));
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
