package com.yellow.trade.integration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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
import java.util.Map;

/**
 * A real PostgreSQL, carrying the real schema.
 *
 * THE SCHEMA IS NOT A FIXTURE. These tests apply sprint-3/db verbatim -- base
 * schema, both migrations, the indexes and the seed -- in the order apply.sh
 * applies them. A hand-written CREATE TABLE in a test resource would drift
 * from the real one within a sprint, and the first thing it would stop
 * catching is a migration that does not apply.
 *
 * Postgres comes from Testcontainers by default. Set IT_DB_URL (with
 * IT_DB_USER and IT_DB_PASSWORD) to point at a database you already have --
 * useful on a machine where the Docker daemon is not running, and the reason
 * these tests could be verified during development here.
 */
abstract class PostgresSupport {

    private static final String SECRET = "an-integration-test-secret-of-32-plus-bytes";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private static final String EXTERNAL_URL = System.getenv("IT_DB_URL");

    private static final PostgreSQLContainer<?> POSTGRES;

    static {
        if (EXTERNAL_URL == null) {
            POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("trading");
            POSTGRES.start();
        } else {
            POSTGRES = null;
        }
    }

    /** The phases of sprint-3/db/apply.sh, in the order it runs them. */
    private static final List<String> SCHEMA_FILES = List.of(
            "base/000_base_schema.sql",
            "migrations/001_schema_migrations.sql",
            "migrations/002_api_alignment.sql",
            "indexes/001_performance_indexes.sql",
            "seed/001_reference_data.sql",
            "seed/002_clients.sql",
            "seed/003_instruments.sql",
            "seed/004_transactions.sql");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        if (EXTERNAL_URL != null) {
            registry.add("spring.datasource.url", () -> EXTERNAL_URL);
            registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("IT_DB_USER", "postgres"));
            registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("IT_DB_PASSWORD", "postgres"));
        } else {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        }
        registry.add("security.jwt.secret", () -> SECRET);
    }

    /**
     * Rebuilds the database from the committed SQL. Dropping the schema first
     * makes each run independent of the last, which matters more than speed:
     * a test that passes only after another test has run is not a test.
     */
    static void applySchema(JdbcTemplate jdbc) {
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");

        Path root = Path.of("..", "sprint-3", "db");
        for (String file : SCHEMA_FILES) {
            try {
                jdbc.execute(Files.readString(root.resolve(file), StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new IllegalStateException("failed applying " + file, e);
            }
        }
    }

    /**
     * The team-owned token fixture, following contracts/auth-api.yaml. It
     * mints tokens and is never deployed; Sprint 8 replaces the issuer and
     * these tests keep working because they depend on the claim set, not on
     * who produced it.
     */
    static HttpHeaders tokenFor(long accountId) {
        String jwt = Jwts.builder()
                .subject(String.valueOf(accountId))
                .claims(Map.of("accountId", accountId, "email", "client" + accountId + "@example.com"))
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
