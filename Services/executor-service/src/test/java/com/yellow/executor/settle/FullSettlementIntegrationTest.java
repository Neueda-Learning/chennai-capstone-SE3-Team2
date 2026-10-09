package com.yellow.executor.settle;

import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;
import com.yellow.executor.fill.FillDecision;
import com.yellow.executor.fill.OrderSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Settlement against a real PostgreSQL: the three writes are atomic, the
 * numbers are right, and ALREADY_SETTLED is idempotent.
 */
@SpringBootTest
@EnabledIf(
        value = "com.yellow.executor.settle.ExecutorPostgresSupport#databaseAvailable",
        disabledReason = "needs a database: start Docker, or set IT_DB_URL "
                + "(with IT_DB_USER and IT_DB_PASSWORD) to a PostgreSQL you already have")
class FullSettlementIntegrationTest extends ExecutorPostgresSupport {

    @Autowired private SettlementPort settlement;
    @Autowired private JdbcTemplate jdbc;

    // Stops the test context from attempting a real Kafka connection when
    // OrderExecutionService is wired.
    @SuppressWarnings("rawtypes")
    @MockBean(name = "tradeEventTemplate")
    private KafkaTemplate tradeEventTemplate;

    private UUID orderId;
    private Long instrumentId;

    @BeforeEach
    void rebuild() {
        applySchema(jdbc);
        orderId = placeOrderAtNew(jdbc);
        instrumentId = itcInstrumentId(jdbc);
        // Mirror what the trade-api does at order placement: block the limit
        // consideration so the balance constraint is satisfied when the fill
        jdbc.update("UPDATE client_account SET blocked_funds = 4200.0000 WHERE client_id = 3");
    }

    @Test
    @DisplayName("fill updates orders, drains balance, releases blocked funds, opens position")
    void fillWritesAllThree() {
        BigDecimal balanceBefore = readBalance(jdbc, 3L);
        BigDecimal executedPrice = new BigDecimal("412.0000");
        BigDecimal consideration = new BigDecimal("4120.0000"); // 10 * 412.0000

        SettlementResult result = settlement.settle(
                new FillDecision.Fill(executedPrice), snapshot());

        assertThat(result, is(SettlementResult.SETTLED));

        // Orders row: status moved and price recorded
        var orderRow = jdbc.queryForMap(
                "SELECT status, fill_price, resolved_at FROM orders WHERE order_id = ?", orderId);
        assertThat(orderRow.get("status"), is("FILLED"));
        assertThat((BigDecimal) orderRow.get("fill_price"), comparesEqualTo(executedPrice));
        assertThat(orderRow.get("resolved_at"), is(notNullValue()));

        // Account: balance debited by actual consideration, blocked_funds zeroed
        assertThat(readBalance(jdbc, 3L),
                comparesEqualTo(balanceBefore.subtract(consideration)));
        assertThat(readBlockedFunds(jdbc, 3L), comparesEqualTo(BigDecimal.ZERO));

        // Position: opened at the executed price
        var posRow = jdbc.queryForMap(
                "SELECT quantity, average_price FROM position "
                        + "WHERE client_id = 3 AND instrument_id = ? AND position_type = 'DELIVERY'",
                instrumentId);
        assertThat((BigDecimal) posRow.get("quantity"),
                comparesEqualTo(new BigDecimal("10")));
        assertThat((BigDecimal) posRow.get("average_price"),
                comparesEqualTo(executedPrice));
    }

    @Test
    @DisplayName("constraint violation rolls back: order stays NEW, balance unchanged")
    void constraintViolationRollsBack() {
        // Overdraft: leave the account unable to cover a fill at 412 * 10 =
        // 4120.
        jdbc.update("UPDATE client_account SET balance = 1.00, blocked_funds = 0 WHERE client_id = 3");

        assertThrows(Exception.class, () ->
                settlement.settle(
                        new FillDecision.Fill(new BigDecimal("412.0000")), snapshot()));

        // All three writes rolled back: order is still NEW
        var row = jdbc.queryForMap("SELECT status FROM orders WHERE order_id = ?", orderId);
        assertThat(row.get("status"), is("NEW"));

        // Balance still 1.00 (the account UPDATE also rolled back)
        assertThat(readBalance(jdbc, 3L), comparesEqualTo(new BigDecimal("1.00")));
    }

    @Test
    @DisplayName("second delivery of a filled order returns ALREADY_SETTLED, nothing changes")
    void secondDeliveryAlreadySettled() {
        settlement.settle(new FillDecision.Fill(new BigDecimal("412.0000")), snapshot());

        BigDecimal balanceAfterFirst = readBalance(jdbc, 3L);

        // Same order, different price: the second delivery must be ignored entirely.
        SettlementResult second = settlement.settle(
                new FillDecision.Fill(new BigDecimal("999.0000")), snapshot());

        assertThat(second, is(SettlementResult.ALREADY_SETTLED));
        assertThat(readBalance(jdbc, 3L), comparesEqualTo(balanceAfterFirst));
    }

    private OrderSnapshot snapshot() {
        return new OrderSnapshot(orderId, 3L, instrumentId, "ITC.NS",
                OrderSide.BUY, new BigDecimal("10"), new BigDecimal("420.00"),
                OrderStatus.NEW);
    }
}
