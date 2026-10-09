package com.yellow.trade.kyc;

import com.yellow.enums.KycStatus;
import com.yellow.trade.integration.PostgresSupport;
import com.yellow.trade.mappers.KycMapper;
import com.yellow.trade.onboarding.ApplicationRequest;
import com.yellow.trade.onboarding.OnboardingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * The job against the real schema: the delay, the guard and the rollback are
 * all SQL, so mocks cannot show them. The scheduled bean is switched off here
 * (PostgresSupport); each test runs the job by hand.
 */
@SpringBootTest
@EnabledIf(
        value = "com.yellow.trade.integration.PostgresSupport#databaseAvailable",
        disabledReason = "needs a database: start Docker, or set IT_DB_URL "
                + "(with IT_DB_USER and IT_DB_PASSWORD) to a PostgreSQL you already have")
class KycJobIntegrationTest extends PostgresSupport {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private OnboardingService onboarding;
    @Autowired private KycMapper mapper;
    @Autowired private KycDecider decider;
    @Autowired private KycProperties properties;
    @Autowired private Clock clock;

    private KycVerificationJob job;

    @BeforeEach
    void rebuildDatabase() {
        applySchema(jdbc);
        job = new KycVerificationJob(mapper, decider, properties, clock);
    }

    private long apply(String pan, String email) {
        return apply(pan, email, "509876543210");
    }

    private long apply(String pan, String email, String bankAccount) {
        return onboarding.apply(new ApplicationRequest("Priya Menon", "1990-05-17", email,
                "+919812345611", pan, "12 Anna Nagar, Chennai", bankAccount, "DEMO0000001"));
    }

    /** As though submitted longer ago than the delay. */
    private void makeDue(long clientId) {
        jdbc.update("UPDATE kyc_verification SET submitted_at = now() - interval '31 seconds' "
                + "WHERE client_id = ?", clientId);
    }

    private Map<String, Object> verification(long clientId) {
        return jdbc.queryForMap("SELECT status, reason, checks::text AS checks, decided_at, attempts, last_error "
                + "FROM kyc_verification WHERE client_id = ?", clientId);
    }

    private String accountKyc(long clientId) {
        return jdbc.queryForObject("SELECT kyc_status FROM client_account WHERE client_id = ?",
                String.class, clientId);
    }

    private int outboxRows(long clientId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE message_key = ?",
                Integer.class, Long.toString(clientId));
    }

    @Test
    @DisplayName("Nothing is decided before the delay; after it, VERIFIED with one outbox row")
    void decidedOnlyAfterTheDelay() {
        long clientId = apply("ABCPM1234Q", "priya@example.com");

        job.run();
        assertThat(verification(clientId).get("status"), is("PENDING"));
        assertThat(accountKyc(clientId), is("PENDING"));
        assertThat(outboxRows(clientId), is(0));

        makeDue(clientId);
        job.run();

        Map<String, Object> row = verification(clientId);
        assertThat(row.get("status"), is("VERIFIED"));
        assertThat(row.get("reason"), is(nullValue()));
        assertThat(row.get("decided_at"), is(notNullValue()));
        assertThat(accountKyc(clientId), is("VERIFIED"));

        Map<String, Object> event = jdbc.queryForMap(
                "SELECT topic, envelope->>'eventType' AS type, envelope->>'source' AS source, "
                        + "envelope->'payload'->>'clientId' AS client, "
                        + "envelope->>'eventId' = event_id::text AS ids_match, published_at "
                        + "FROM outbox_event WHERE message_key = ?", Long.toString(clientId));
        assertThat(event.get("topic"), is("kyc-events"));
        assertThat(event.get("type"), is("KYC_VERIFIED"));
        assertThat(event.get("source"), is("kyc-service"));
        assertThat(event.get("client"), is(Long.toString(clientId)));
        assertThat(event.get("ids_match"), is(true));
        assertThat(event.get("published_at"), is(nullValue()));
    }

    @Test
    @DisplayName("A PAN the registry does not know is REJECTED on both tables, with its reason, and queues nothing")
    void rejected() {
        long clientId = apply("ABCPS0000A", "priya@example.com");
        makeDue(clientId);

        job.run();

        Map<String, Object> row = verification(clientId);
        assertThat(row.get("status"), is("REJECTED"));
        assertThat(row.get("reason"), is(StubKycProvider.NOT_FOUND));
        assertThat(row.get("checks"), is("{\"age\": \"pass\", \"registry\": \"fail\", \"panHolderType\": \"pass\"}"));
        assertThat(accountKyc(clientId), is("REJECTED"));
        assertThat(outboxRows(clientId), is(0));
    }

    @Test
    @DisplayName("Deciding the same customer twice changes nothing the second time")
    void decidedOnce() {
        long clientId = apply("ABCPM1234Q", "priya@example.com");
        makeDue(clientId);

        assertThat(decider.decide(clientId), is(Optional.of(KycStatus.VERIFIED)));
        Object decidedAt = verification(clientId).get("decided_at");

        assertThat(decider.decide(clientId), is(Optional.empty()));
        job.run();

        assertThat(verification(clientId).get("decided_at"), is(decidedAt));
        assertThat(outboxRows(clientId), is(1));
    }

    @Test
    @DisplayName("A failure partway through one customer rolls back to PENDING and does not stop the next")
    void failureLeavesPending() {
        long broken = apply("ABCPM1234Q", "priya@example.com");
        long fine = apply("ABCPN5678R", "nikhil@example.com");
        // The account disagreeing with its verification makes the second write
        // fail after the first has already happened.
        jdbc.update("UPDATE client_account SET kyc_status = 'VERIFIED' WHERE client_id = ?", broken);
        makeDue(broken);
        makeDue(fine);

        job.run();

        Map<String, Object> row = verification(broken);
        assertThat(row.get("status"), is("PENDING"));
        assertThat(row.get("decided_at"), is(nullValue()));
        assertThat(row.get("attempts"), is(1));
        assertThat(row.get("last_error"), is("IllegalStateException"));
        assertThat(outboxRows(broken), is(0));

        assertThat(verification(fine).get("status"), is("VERIFIED"));
        assertThat(outboxRows(fine), is(1));
    }

    @Test
    @DisplayName("At max attempts a customer is set aside, no longer blocks the queue, and returns once attempts is reset")
    void setAsideAfterMaxAttempts() {
        KycVerificationJob twoTries = new KycVerificationJob(mapper, decider,
                new KycProperties(properties.topic(), properties.delay(), properties.batchSize(), 2), clock);
        long broken = apply("ABCPM1234Q", "priya@example.com");
        jdbc.update("UPDATE client_account SET kyc_status = 'VERIFIED' WHERE client_id = ?", broken);
        makeDue(broken);

        twoTries.run();
        twoTries.run();
        assertThat(verification(broken).get("attempts"), is(2));

        // Set aside: not picked up, so not counted again.
        twoTries.run();
        assertThat(verification(broken).get("attempts"), is(2));
        assertThat(verification(broken).get("status"), is("PENDING"));

        // Someone fixes the cause and puts the customer back in the queue.
        jdbc.update("UPDATE client_account SET kyc_status = 'PENDING' WHERE client_id = ?", broken);
        jdbc.update("UPDATE kyc_verification SET attempts = 0 WHERE client_id = ?", broken);
        twoTries.run();

        assertThat(verification(broken).get("status"), is("VERIFIED"));
        assertThat(outboxRows(broken), is(1));
    }

    @Test
    @DisplayName("The application's bank account is stored, and one that cannot be verified is REJECTED on both tables")
    void bankAccountChecked() {
        long clientId = apply("ABCPM1234Q", "priya@example.com", "509876540000");
        assertThat(jdbc.queryForMap("SELECT account_number, ifsc, holder_name FROM bank_account WHERE client_id = ?", clientId),
                is(Map.of("account_number", "509876540000", "ifsc", "DEMO0000001", "holder_name", "Priya Menon")));
        makeDue(clientId);

        job.run();

        Map<String, Object> row = verification(clientId);
        assertThat(row.get("status"), is("REJECTED"));
        assertThat(row.get("reason"), is(StubKycProvider.BANK_NOT_VERIFIED));
        assertThat(accountKyc(clientId), is("REJECTED"));
        assertThat(outboxRows(clientId), is(0));
    }

    @Test
    @DisplayName("Every seeded customer has a bank account that passes the check")
    void seededBankAccounts() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM bank_account WHERE client_id BETWEEN 1 AND 10 AND account_number NOT LIKE '%0000'",
                Integer.class), is(10));
    }
}

