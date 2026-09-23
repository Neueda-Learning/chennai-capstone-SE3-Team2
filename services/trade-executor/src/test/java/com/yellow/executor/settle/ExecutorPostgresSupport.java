package com.yellow.executor.settle;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * A real PostgreSQL, built from the Sprint 3 files rather than from a hand-
 * written fixture. The schema under test has to be the schema that ships.
 */
public abstract class ExecutorPostgresSupport {

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

    private static synchronized PostgreSQLContainer<?> container() {
        if (postgres == null) {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("trading");
            postgres.start();
        }
        return postgres;
    }

    /** The phases of data/db/apply.sh, in the order it runs them. */
    private static final List<String> SCHEMA_FILES = List.of(
            "base/000_base_schema.sql",
            "migrations/001_schema_migrations.sql",
            "migrations/002_api_alignment.sql",
            "migrations/003_account_last_updated.sql",
            "migrations/004_execution_columns.sql",
            "indexes/001_performance_indexes.sql",
            "seed/001_reference_data.sql",
            "seed/002_clients.sql",
            "seed/003_instruments.sql",
            "seed/004_transactions.sql",
            "seed/005_fauxnance_instruments.sql");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        if (EXTERNAL_URL != null) {
            registry.add("spring.datasource.url", () -> EXTERNAL_URL);
            registry.add("spring.datasource.username",
                    () -> System.getenv().getOrDefault("IT_DB_USER", "postgres"));
            registry.add("spring.datasource.password",
                    () -> System.getenv().getOrDefault("IT_DB_PASSWORD", "postgres"));
        } else {
            PostgreSQLContainer<?> db = container();
            registry.add("spring.datasource.url", db::getJdbcUrl);
            registry.add("spring.datasource.username", db::getUsername);
            registry.add("spring.datasource.password", db::getPassword);
        }
        // The executor refuses to start without a key. These tests never
        // reach Fauxnance -- they exercise settlement -- so a placeholder is
        registry.add("fauxnance.api-key", () -> "integration-test-key");
        // No broker in this test. The listener container would otherwise spend
        // the run retrying a connection nobody is waiting on.
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
    }

    /** Rebuild from empty, so each test starts from the seeded state. */
    protected static void applySchema(JdbcTemplate jdbc) {
        jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
        Path root = Path.of("..", "..", "data", "db");
        for (String file : SCHEMA_FILES) {
            try {
                jdbc.execute(Files.readString(root.resolve(file)));
            } catch (Exception e) {
                throw new IllegalStateException("could not apply " + file, e);
            }
        }
    }

    /** ITC.NS: priced in the hundreds, so an order on it fits a seeded balance. */
    protected static Long itcInstrumentId(JdbcTemplate jdbc) {
        return jdbc.queryForObject(
                "SELECT instrument_id FROM equity WHERE ticker = 'ITC.NS'", Long.class);
    }

    protected static BigDecimal readBalance(JdbcTemplate jdbc, Long clientId) {
        return jdbc.queryForObject(
                "SELECT balance FROM client_account WHERE client_id = ?",
                BigDecimal.class, clientId);
    }

    protected static BigDecimal readBlockedFunds(JdbcTemplate jdbc, Long clientId) {
        return jdbc.queryForObject(
                "SELECT blocked_funds FROM client_account WHERE client_id = ?",
                BigDecimal.class, clientId);
    }

    /** One order sitting at NEW, waiting for the executor to decide about it. */
    protected static UUID placeOrderAtNew(JdbcTemplate jdbc) {
        UUID orderId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO orders (order_id, client_id, instrument_id, side, order_type,
                                    product_type, price, quantity, status, idempotency_key)
                VALUES (?, 3, ?, 'BUY', 'MARKET', 'CNC', 420.00, 10, 'NEW', ?)
                """, orderId, itcInstrumentId(jdbc), "exec-it-" + orderId);
        return orderId;
    }
}
