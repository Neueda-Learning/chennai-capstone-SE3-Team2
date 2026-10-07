package com.yellow.trade.integration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.testcontainers.DockerClientFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.Map;

public abstract class PostgresSupport {

    private static final String ISSUER = "auth-service";
    private static final String SECRET = "an-integration-test-secret-of-32-plus-bytes";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private static final String EXTERNAL_URL = System.getenv("IT_DB_URL");

    private static PostgreSQLContainer<?> postgres;

    public static boolean databaseAvailable() {
        if (EXTERNAL_URL != null) {
            return true;
        }
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Started on first use, so that the class can be skipped without one. */
    private static synchronized PostgreSQLContainer<?> container() {
        if (postgres == null) {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("trading");
            postgres.start();
        }
        return postgres;
    }

    /**
     * sh, in the order it runs them. NOT A GLOB, DELIBERATELY: the order
     * matters and an accidental file in one of those folders should not
     * silently join the schema a test runs against.
     */
    private static final List<String> SCHEMA_FILES = List.of(
            "base/000_base_schema.sql",
            "migrations/001_schema_migrations.sql",
            "migrations/002_api_alignment.sql",
            "migrations/003_account_last_updated.sql",
            "migrations/004_execution_columns.sql",
            "migrations/005_drop_client_auth.sql",
            "migrations/006_activation_email.sql",
            "migrations/007_kyc.sql",
            "migrations/008_kyc_attempts.sql",
            "migrations/009_bank_account.sql",
            "migrations/010_payments.sql",
            "migrations/011_preferences.sql",
            "migrations/012_notifications.sql",
            "migrations/013_watchlists.sql",
            "migrations/014_portfolio.sql",
            "indexes/001_performance_indexes.sql",
            "seed/001_reference_data.sql",
            "seed/002_clients.sql",
            "seed/003_instruments.sql",
            "seed/004_transactions.sql",
            "seed/005_fauxnance_instruments.sql",
            "seed/006_bank_accounts.sql",
            "seed/007_mf_nav_funds.sql");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        if (EXTERNAL_URL != null) {
            registry.add("spring.datasource.url", () -> EXTERNAL_URL);
            registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("IT_DB_USER", "postgres"));
            registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("IT_DB_PASSWORD", "postgres"));
        } else {
            PostgreSQLContainer<?> db = container();
            registry.add("spring.datasource.url", db::getJdbcUrl);
            registry.add("spring.datasource.username", db::getUsername);
            registry.add("spring.datasource.password", db::getPassword);
        }
        registry.add("security.jwt.secret", () -> SECRET);
        registry.add("security.jwt.issuer", () -> ISSUER);

        // The activation mailer's required settings. Placeholders: its
        // listener does not start here, and nothing is ever sent.
        registry.add("activation.internal-secret", () -> "an-integration-test-internal-secret");
        registry.add("activation.mail-from", () -> "noreply@example.invalid");
        registry.add("activation.consumer.auto-startup", () -> "false");
        registry.add("spring.mail.username", () -> "noreply@example.invalid");
        registry.add("spring.mail.password", () -> "not-a-real-password");

        // The KYC job would decide rows a test is still arranging, and there
        // is no broker for the relay. Tests that need either run it by hand.
        registry.add("kyc.job.enabled", () -> "false");
        registry.add("outbox.relay.enabled", () -> "false");
        registry.add("payments.job.enabled", () -> "false");
        // Notifications: no broker to consume from, and nothing is ever sent;
        // the tests that need the consumer or the dispatcher call them.
        registry.add("notifications.consumer.auto-startup", () -> "false");
        registry.add("notifications.dispatch.enabled", () -> "false");
        registry.add("watchlists.consumer.auto-startup", () -> "false");
        // Portfolio: no broker to consume trade-events from; tests call the book.
        registry.add("portfolio.consumer.auto-startup", () -> "false");
        // Advice: no broker for market-data, and no timer; tests ask for a signal.
        registry.add("advice.consumer.auto-startup", () -> "false");
        registry.add("advice.refresh.enabled", () -> "false");
    }

    public static void applySchema(JdbcTemplate jdbc) {
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");

        Path root = Path.of("..", "..", "data", "db");
        for (String file : SCHEMA_FILES) {
            try {
                jdbc.execute(Files.readString(root.resolve(file), StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new IllegalStateException("failed applying " + file, e);
            }
        }
    }

    public static HttpHeaders tokenFor(long accountId) {
        String jwt = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .claims(Map.of("accountId", accountId, "roles", List.of("CUSTOMER")))
                .issuedAt(Date.from(Instant.now().minusSeconds(1)))
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(KEY)
                .compact();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return headers;
    }
}
