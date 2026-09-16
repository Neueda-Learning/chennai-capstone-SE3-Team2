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


abstract class PostgresSupport {

    private static final String ISSUER = "auth-service";
    private static final String SECRET = "an-integration-test-secret-of-32-plus-bytes";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private static final String EXTERNAL_URL = System.getenv("IT_DB_URL");

    private static PostgreSQLContainer<?> postgres;

    static boolean databaseAvailable() {
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

    /** The phases of sprint-3/db/apply.sh, in the order it runs them. */
    private static final List<String> SCHEMA_FILES = List.of(
            "base/000_base_schema.sql",
            "migrations/001_schema_migrations.sql",
            "migrations/002_api_alignment.sql",
            "migrations/003_account_last_updated.sql",
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
            PostgreSQLContainer<?> db = container();
            registry.add("spring.datasource.url", db::getJdbcUrl);
            registry.add("spring.datasource.username", db::getUsername);
            registry.add("spring.datasource.password", db::getPassword);
        }
        registry.add("security.jwt.secret", () -> SECRET);
        registry.add("security.jwt.issuer", () -> ISSUER);
    }


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

    static HttpHeaders tokenFor(long accountId) {
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
