package com.yellow.executor.settle;

import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;
import com.yellow.executor.fill.FillDecision;
import com.yellow.executor.fill.OrderSnapshot;
import com.yellow.executor.fill.RejectReason;
import com.yellow.executor.persistence.ExecutionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The guarded state transition against a real PostgreSQL.
 *
 * <p>This is the one part of the duplicate mechanism that cannot be proved with
 * a double. The claim is that the DATABASE serialises two concurrent UPDATEs
 * conditioned on the same row's state, so that exactly one of them matches a
 * row however many deliveries arrive together. An in-memory map returns zero on
 * the second call because it was written to; Postgres returns zero because of
 * how row locking works, and only the second is evidence.
 *
 * <p>Skips rather than fails without Docker, the same way the Trade REST API's
 * integration suite does. If it skips, the duplicate mechanism is UNVERIFIED --
 * which is worth knowing before a review where demonstrating it is a criterion.
 */
@SpringBootTest
@EnabledIf(
        value = "com.yellow.executor.settle.ExecutorPostgresSupport#databaseAvailable",
        disabledReason = "needs a database: start Docker, or set IT_DB_URL "
                + "(with IT_DB_USER and IT_DB_PASSWORD) to a PostgreSQL you already have")
class GuardedSettlementIntegrationTest extends ExecutorPostgresSupport {

    @Autowired private SettlementPort settlement;
    @Autowired private ExecutionMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    private UUID orderId;

    @BeforeEach
    void rebuild() {
        applySchema(jdbc);
        orderId = placeOrderAtNew(jdbc);
    }

    @Test
    @DisplayName("a fill writes status, price and time together")
    void aFillWritesAllThree() {
        SettlementResult result = settlement.settle(
                new FillDecision.Fill(new BigDecimal("412.4100")), snapshot());

        assertThat(result, is(SettlementResult.SETTLED));

        var row = jdbc.queryForMap(
                "SELECT status, fill_price, resolved_at, rejection_reason "
                        + "FROM orders WHERE order_id = ?", orderId);
        assertThat(row.get("status"), is("FILLED"));
        assertThat((BigDecimal) row.get("fill_price"), comparesEqualTo(new BigDecimal("412.4100")));
        // ck_orders_resolved_at_matches_status would have refused the write
        // without this, which is why status and resolved_at move together.
        assertThat(row.get("resolved_at"), is(org.hamcrest.Matchers.notNullValue()));
        assertThat(row.get("rejection_reason"), is(nullValue()));
    }

    @Test
    @DisplayName("a rejection writes the reason and no price")
    void aRejectionWritesTheReason() {
        settlement.settle(new FillDecision.Reject(RejectReason.NO_PRICE), snapshot());

        var row = jdbc.queryForMap(
                "SELECT status, fill_price, rejection_reason FROM orders WHERE order_id = ?", orderId);
        assertThat(row.get("status"), is("REJECTED"));
        assertThat(row.get("fill_price"), is(nullValue()));
        assertThat(row.get("rejection_reason"), is("NO_PRICE"));
    }

    @Test
    @DisplayName("a second delivery affects zero rows and changes nothing")
    void secondDeliveryAffectsZeroRows() {
        settlement.settle(new FillDecision.Fill(new BigDecimal("412.4100")), snapshot());

        // The same message again: the executor is handed the same order twice
        // during a rebalance, after a crash between the database commit and the
        // offset commit, or when somebody replays the topic.
        SettlementResult second = settlement.settle(
                new FillDecision.Fill(new BigDecimal("999.9900")), snapshot());

        assertThat(second, is(SettlementResult.ALREADY_SETTLED));

        // Crucially the price did NOT move to the second delivery's figure.
        var row = jdbc.queryForMap(
                "SELECT status, fill_price FROM orders WHERE order_id = ?", orderId);
        assertThat(row.get("status"), is("FILLED"));
        assertThat((BigDecimal) row.get("fill_price"), comparesEqualTo(new BigDecimal("412.4100")));
    }

    @Test
    @DisplayName("eight deliveries racing on one order: exactly one settles it")
    void concurrentDeliveriesAreSerialisedByTheDatabase() throws Exception {
        int deliveries = 8;
        ExecutorService pool = Executors.newFixedThreadPool(deliveries);

        List<Callable<SettlementResult>> attempts = IntStream.range(0, deliveries)
                .<Callable<SettlementResult>>mapToObj(i -> () ->
                        settlement.settle(new FillDecision.Fill(new BigDecimal("412.4100")), snapshot()))
                .toList();

        List<Future<SettlementResult>> results = pool.invokeAll(attempts);
        pool.shutdown();

        long settled = 0;
        for (Future<SettlementResult> future : results) {
            if (future.get() == SettlementResult.SETTLED) {
                settled++;
            }
        }

        // This is the whole mechanism. A read, then a decision, then a write
        // would let several of these through, because every read would see NEW
        // before any write landed. A write conditioned on the state it expects
        // cannot, because the database serialises them.
        assertThat(settled, is(1L));

        Integer terminal = jdbc.queryForObject(
                "SELECT count(*) FROM orders WHERE order_id = ? AND status = 'FILLED'",
                Integer.class, orderId);
        assertThat(terminal, is(1));
    }

    @Test
    @DisplayName("the database refuses a reason on a filled order")
    void reasonOnAFilledOrderIsRefused() {
        // ck_orders_reason_matches_status, from migration 004. A FILLED order
        // carrying INSUFFICIENT_FUNDS is a contradiction, and the schema says
        // so rather than trusting every writer to remember.
        assertThrows(Exception.class, () -> mapper.settleIfNew(
                orderId, "FILLED", new BigDecimal("412.41"), Instant.now(), "INSUFFICIENT_FUNDS"));
    }

    private OrderSnapshot snapshot() {
        return new OrderSnapshot(orderId, 3L, itcInstrumentId(jdbc), "ITC.NS",
                OrderSide.BUY, new BigDecimal("10"), new BigDecimal("420.00"),
                OrderStatus.NEW);
    }
}
