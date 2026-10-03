package com.yellow.trade.payments;

import com.yellow.trade.integration.PostgresSupport;
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

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;

/**
 * Payments over HTTP against the real schema: the token filter, the routes,
 * the SQL and the transactions. The job is switched off here
 * (PostgresSupport); each test has the decider decide by hand.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIf(
        value = "com.yellow.trade.integration.PostgresSupport#databaseAvailable",
        disabledReason = "needs a database: start Docker, or set IT_DB_URL "
                + "(with IT_DB_USER and IT_DB_PASSWORD) to a PostgreSQL you already have")
class PaymentIntegrationTest extends PostgresSupport {

    /** Seeded: ACTIVE, KYC VERIFIED, with a bank account. */
    private static final long ACCOUNT = 3L;

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PaymentDecider decider;

    @BeforeEach
    void rebuildDatabase() {
        applySchema(jdbc);
    }

    private ResponseEntity<Map> post(long asAccount, String path, String amount, String key) {
        HttpHeaders headers = tokenFor(asAccount);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"amount\":" + amount + ",\"idempotencyKey\":\"" + key + "\"}";
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    private ResponseEntity<Map> deposit(String amount, String key) {
        return post(ACCOUNT, "/api/v1/accounts/3/deposits", amount, key);
    }

    private ResponseEntity<Map> withdraw(String amount, String key) {
        return post(ACCOUNT, "/api/v1/accounts/3/withdrawals", amount, key);
    }

    private Map<String, Object> cash() {
        return jdbc.queryForMap("SELECT balance, blocked_funds, version FROM client_account WHERE client_id = ?", ACCOUNT);
    }

    private static long id(ResponseEntity<Map> response) {
        return ((Number) response.getBody().get("transferId")).longValue();
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    @Test
    @DisplayName("A deposit is PENDING with nothing credited, then credited once the gateway says yes")
    void depositCredited() {
        Map<String, Object> before = cash();

        ResponseEntity<Map> response = deposit("5000.50", key());

        assertThat(response.getStatusCode(), is(HttpStatus.ACCEPTED));
        assertThat(response.getBody().get("status"), is("PENDING"));
        assertThat((BigDecimal) cash().get("balance"), comparesEqualTo((BigDecimal) before.get("balance")));

        assertThat(decider.decide(id(response)).orElseThrow(), is("SUCCESS"));

        Map<String, Object> after = cash();
        assertThat((BigDecimal) after.get("balance"),
                comparesEqualTo(((BigDecimal) before.get("balance")).add(new BigDecimal("5000.50"))));
        assertThat((Integer) after.get("version"), is((Integer) before.get("version") + 1));
    }

    @Test
    @DisplayName("A deposit over the gateway's limit FAILS with its reason, and credits nothing")
    void depositDeclined() {
        Map<String, Object> before = cash();
        ResponseEntity<Map> response = deposit("250000", key());

        assertThat(decider.decide(id(response)).orElseThrow(), is("FAILED"));

        assertThat(jdbc.queryForObject("SELECT reason FROM fund_transfer WHERE transfer_id = ?", String.class, id(response)),
                is(StubPaymentGateway.OVER_LIMIT));
        assertThat((BigDecimal) cash().get("balance"), comparesEqualTo((BigDecimal) before.get("balance")));
    }

    @Test
    @DisplayName("A withdrawal holds its amount at once, then leaves the account once the gateway says yes")
    void withdrawalSettled() {
        Map<String, Object> before = cash();

        ResponseEntity<Map> response = withdraw("1000", key());

        assertThat(response.getStatusCode(), is(HttpStatus.ACCEPTED));
        assertThat((BigDecimal) cash().get("blocked_funds"),
                comparesEqualTo(((BigDecimal) before.get("blocked_funds")).add(new BigDecimal("1000"))));

        decider.decide(id(response));

        Map<String, Object> after = cash();
        assertThat((BigDecimal) after.get("balance"),
                comparesEqualTo(((BigDecimal) before.get("balance")).subtract(new BigDecimal("1000"))));
        assertThat((BigDecimal) after.get("blocked_funds"), comparesEqualTo((BigDecimal) before.get("blocked_funds")));
    }

    @Test
    @DisplayName("A withdrawal over the available cash is PAY-400, and writes nothing at all")
    void withdrawalOverAvailable() {
        Map<String, Object> before = cash();
        BigDecimal available = ((BigDecimal) before.get("balance")).subtract((BigDecimal) before.get("blocked_funds"));
        String key = key();

        ResponseEntity<Map> response = withdraw(available.add(BigDecimal.ONE).setScale(2).toPlainString(), key);

        assertThat(response.getStatusCode(), is(HttpStatus.BAD_REQUEST));
        assertThat(response.getBody().get("errorCode"), is("PAY-400"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fund_transfer WHERE idempotency_key = ?", Integer.class, key), is(0));
        assertThat(cash(), is(before));
    }

    @Test
    @DisplayName("A declined withdrawal releases its hold")
    void withdrawalReleased() {
        jdbc.update("UPDATE client_account SET balance = balance + 300000 WHERE client_id = ?", ACCOUNT);
        Map<String, Object> before = cash();

        ResponseEntity<Map> response = withdraw("250000", key());
        assertThat(decider.decide(id(response)).orElseThrow(), is("FAILED"));

        Map<String, Object> after = cash();
        assertThat((BigDecimal) after.get("balance"), comparesEqualTo((BigDecimal) before.get("balance")));
        assertThat((BigDecimal) after.get("blocked_funds"), comparesEqualTo((BigDecimal) before.get("blocked_funds")));
    }

    @Test
    @DisplayName("The same transfer sent twice is one transfer, held once")
    void replay() {
        Map<String, Object> before = cash();
        String key = key();

        long first = id(withdraw("1000", key));
        long second = id(withdraw("1000.00", key));

        assertThat(second, is(first));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fund_transfer WHERE idempotency_key = ?", Integer.class, key), is(1));
        assertThat((BigDecimal) cash().get("blocked_funds"),
                comparesEqualTo(((BigDecimal) before.get("blocked_funds")).add(new BigDecimal("1000"))));
    }

    @Test
    @DisplayName("Another customer's account, or an account whose KYC has not passed, is ACC-403")
    void notAllowed() {
        assertThat(post(ACCOUNT, "/api/v1/accounts/4/deposits", "100", key()).getBody().get("errorCode"), is("ACC-403"));
        assertThat(post(9L, "/api/v1/accounts/9/deposits", "100", key()).getBody().get("errorCode"), is("ACC-403"));
    }

    @Test
    @DisplayName("The bank account comes back masked; transfers come back newest first")
    void reads() {
        ResponseEntity<Map> bank = rest.exchange("/api/v1/accounts/3/bank-account", HttpMethod.GET,
                new HttpEntity<>(tokenFor(ACCOUNT)), Map.class);
        assertThat(bank.getBody().get("accountNumberLast4"), is("0031"));
        assertThat(bank.getBody().get("ifsc"), is("DEMO0000001"));

        long newest = id(deposit("10", key()));
        ResponseEntity<Map[]> list = rest.exchange("/api/v1/accounts/3/transfers", HttpMethod.GET,
                new HttpEntity<>(tokenFor(ACCOUNT)), Map[].class);
        assertThat(((Number) list.getBody()[0].get("transferId")).longValue(), is(newest));
    }
}
