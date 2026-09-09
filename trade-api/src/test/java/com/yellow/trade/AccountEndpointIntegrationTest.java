package com.yellow.trade;

import com.yellow.trade.dto.AccountResponse;
import com.yellow.trade.dto.BalanceResponse;
import com.yellow.trade.dto.ErrorResponse;
import com.yellow.trade.dto.PositionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;


@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountEndpointIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withInitScript("schema.sql");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        // fresh state per test -- order matters, child tables first
        jdbcTemplate.execute("DELETE FROM position");
        jdbcTemplate.execute("DELETE FROM orders");
        jdbcTemplate.execute("DELETE FROM instrument");
        jdbcTemplate.execute("DELETE FROM account");
    }

    private long seedAccount(String holderName, BigDecimal balance, String state, long version) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO account (holder_name, email, phone_number, demat_id, pan, balance, account_state, version) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING account_id",
                Long.class,
                holderName, holderName.toLowerCase().replace(" ", ".") + "@example.com",
                "9876543210", "DEMAT" + System.nanoTime(), "PAN" + (System.nanoTime() % 10000000),
                balance, state, version);
    }

    private long seedInstrument(String ticker) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO instrument (ticker, isin, trading_status) VALUES (?, ?, 'TRADING') RETURNING instrument_id",
                Long.class, ticker, "ISIN" + System.nanoTime() % 100000000);
    }

    private void seedPosition(long accountId, long instrumentId, int quantity, BigDecimal averagePrice) {
        jdbcTemplate.update(
                "INSERT INTO position (account_id, instrument_id, quantity, average_price) VALUES (?, ?, ?, ?)",
                accountId, instrumentId, quantity, averagePrice);
    }

    @Test
    void getAccountReturnsKnownFieldsForExistingAccount() {
        long id = seedAccount("Priya Menon", new BigDecimal("24500.75"), "ACTIVE", 7);

        ResponseEntity<AccountResponse> response =
                restTemplate.getForEntity("/api/v1/accounts/" + id, AccountResponse.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.OK)));
        assertThat(response.getBody().getHolderName(), is(equalTo("Priya Menon")));
        assertThat(response.getBody().getCashBalance(), is(comparesEqualTo(new BigDecimal("24500.75"))));
        assertThat(response.getBody().getStatus().name(), is(equalTo("ACTIVE")));
        assertThat(response.getBody().getVersion(), is(equalTo(7)));

        // accountId (string business ref) has no source column in the schema yet --
        // asserting existence only, not a specific value, until that's resolved
        assertThat(response.getBody().getAccountId(), is(notNullValue()));
    }

    @Test
    void getAccountReturns404WithAccCodeForUnknownAccount() {
        ResponseEntity<ErrorResponse> response =
                restTemplate.getForEntity("/api/v1/accounts/999999", ErrorResponse.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.NOT_FOUND)));
        assertThat(response.getBody().getErrorCode(), is(equalTo("ACC-404")));
    }

    @Test
    void getBalanceReturnsCashBalanceForExistingAccount() {
        long id = seedAccount("Arjun Rao", new BigDecimal("1000.00"), "ACTIVE", 1);

        ResponseEntity<BalanceResponse> response =
                restTemplate.getForEntity("/api/v1/accounts/" + id + "/balance", BalanceResponse.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.OK)));
        assertThat(response.getBody().getCashBalance(), is(comparesEqualTo(new BigDecimal("1000.00"))));

        // currency has no source column in the schema yet -- same open question as accountId
        assertThat(response.getBody().getCurrency(), is(notNullValue()));
    }

    @Test
    void getPositionsReturnsOnlyNonZeroHoldings() {
        long accountId = seedAccount("Kavya Nair", new BigDecimal("5000.00"), "ACTIVE", 1);
        long heldInstrument = seedInstrument("ACME");
        long closedInstrument = seedInstrument("MSFT");

        seedPosition(accountId, heldInstrument, 100, new BigDecimal("25.50"));
        seedPosition(accountId, closedInstrument, 0, new BigDecimal("300.00"));

        ResponseEntity<PositionResponse[]> response =
                restTemplate.getForEntity("/api/v1/accounts/" + accountId + "/positions", PositionResponse[].class);

        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.OK)));
        assertThat(response.getBody(), arrayWithSize(1));
        assertThat(response.getBody()[0].getSymbol(), is(equalTo("ACME")));
        assertThat(response.getBody()[0].getQuantity(), is(equalTo(100)));
    }

    private void seedOrder(java.util.UUID orderId, long accountId, long instrumentId, String idempotencyKey,
                           String status, java.time.Instant createdOn) {
        // assumes order_id is now UUID, inserted by the app (not DB-generated) --
        // matches how Order.place() already generates its own UUID.randomUUID()
        jdbcTemplate.update(
                "INSERT INTO orders (order_id, account_id, instrument_id, idempotency_key, side, " +
                        "order_type, quantity, price, status, received_at) " +
                        "VALUES (?, ?, ?, ?, 'BUY', 'LIMIT', ?, ?, ?, ?)",
                orderId, accountId, instrumentId, idempotencyKey, 100, new BigDecimal("25.50"), status, createdOn);
    }

    @Test
    void getOrdersReturnsHistoryForAccount() {
        long accountId = seedAccount("Rahul Iyer", new BigDecimal("2000.00"), "ACTIVE", 1);
        long instrumentId = seedInstrument("ACME");

        java.util.UUID orderId = java.util.UUID.randomUUID();
        seedOrder(orderId, accountId, instrumentId, "key-1234", "NEW", java.time.Instant.now());

        ResponseEntity<com.yellow.trade.dto.OrderHistoryEntry[]> response = restTemplate.getForEntity(
                "/api/v1/accounts/" + accountId + "/orders",
                com.yellow.trade.dto.OrderHistoryEntry[].class);

        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.OK)));
        assertThat(response.getBody(), arrayWithSize(1));
        assertThat(response.getBody()[0].getOrderId(), is(equalTo("ORD-" + orderId)));
        assertThat(response.getBody()[0].getSymbol(), is(equalTo("ACME")));
        assertThat(response.getBody()[0].getStatus().name(), is(equalTo("NEW")));
    }
}