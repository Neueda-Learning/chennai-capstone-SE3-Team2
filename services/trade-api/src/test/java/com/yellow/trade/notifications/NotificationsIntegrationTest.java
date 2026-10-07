package com.yellow.trade.notifications;

import com.yellow.trade.integration.PostgresSupport;
import com.yellow.trade.notifications.api.AlertDelivery;
import com.yellow.trade.notifications.api.AlertNotice;
import com.yellow.trade.notifications.api.DeliveryReceipt;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The ledger against the real schema: the unique source key, the consumer's
 * record, the dispatcher reading the real preferences module, the inbox over
 * HTTP, and a cancel reaching trade-events through the outbox.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIf(
        value = "com.yellow.trade.integration.PostgresSupport#databaseAvailable",
        disabledReason = "needs a database: start Docker, or set IT_DB_URL "
                + "(with IT_DB_USER and IT_DB_PASSWORD) to a PostgreSQL you already have")
class NotificationsIntegrationTest extends PostgresSupport {

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TradeEventsListener listener;
    @Autowired private NotificationDispatcher dispatcher;
    @Autowired private AlertDelivery alerts;
    @Autowired private TransactionTemplate transaction;

    /** Nothing is ever mailed from a test: the sender is a mock, and what it was asked to send is checked. */
    @MockitoBean private NotificationMailSender mail;

    @BeforeEach
    void rebuildDatabase() {
        applySchema(jdbc);
    }

    private static String tradeEvent(UUID eventId, String type, long account, String executedPrice, String reason) {
        return """
                {"eventId":"%s","eventType":"%s","eventTime":"2026-10-06T04:00:00Z","source":"trade-executor",
                 "schemaVersion":1,"payload":{"orderId":"%s","accountId":%d,"symbol":"ITC.NS","side":"BUY",
                 "quantity":2,"price":400.00,"executedPrice":%s,"status":"X","reason":%s,"cashDelta":0,
                 "positionQuantityAfter":null,"averageCostAfter":null,"executedOn":"2026-10-06T04:00:00Z"}}"""
                .formatted(eventId, type, UUID.randomUUID(), account, executedPrice,
                        reason == null ? "null" : "\"" + reason + "\"");
    }

    private ResponseEntity<Map> call(long asAccount, HttpMethod method, String path, String body) {
        HttpHeaders headers = tokenFor(asAccount);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    private ResponseEntity<List> history(long asAccount, long account) {
        return rest.exchange("/api/v1/accounts/" + account + "/notifications", HttpMethod.GET,
                new HttpEntity<>(tokenFor(asAccount)), List.class);
    }

    private Map<String, Object> only(long account) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT kind, status, channel, destination, subject FROM notif_notification WHERE client_id = ?", account);
        assertThat(rows, hasSize(1));
        return rows.get(0);
    }

    @Test
    @DisplayName("a fill, a rejection and a cancellation each notify")
    void eachOutcomeNotifies() {
        listener.onTradeEvent(tradeEvent(UUID.randomUUID(), "ORDER_FILLED", 3, "399.50", null));
        listener.onTradeEvent(tradeEvent(UUID.randomUUID(), "ORDER_REJECTED", 3, "null", "INSUFFICIENT_FUNDS"));
        listener.onTradeEvent(tradeEvent(UUID.randomUUID(), "ORDER_CANCELLED", 3, "null", "CANCELLED_BY_CUSTOMER"));

        assertThat(jdbc.queryForList("SELECT kind FROM notif_notification WHERE client_id = 3 ORDER BY kind", String.class),
                is(List.of("ORDER_CANCELLED", "ORDER_FILLED", "ORDER_REJECTED")));
        assertThat(jdbc.queryForList("SELECT DISTINCT status FROM notif_notification", String.class), is(List.of("QUEUED")));
    }

    @Test
    @DisplayName("a replayed event records nothing twice and sends once")
    void replaySendsOnce() {
        String event = tradeEvent(UUID.randomUUID(), "ORDER_FILLED", 3, "399.50", null);

        listener.onTradeEvent(event);
        dispatcher.dispatchOnce();
        listener.onTradeEvent(event);
        dispatcher.dispatchOnce();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM notif_notification", Integer.class), is(1));
        verify(mail, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("nothing stored: email to the profile's address, recorded with the channel and the address masked")
    void defaultChannel() {
        listener.onTradeEvent(tradeEvent(UUID.randomUUID(), "ORDER_FILLED", 3, "399.50", null));

        dispatcher.dispatchOnce();

        verify(mail).send(eq("rohan.nair@example.com"), eq("Bought 2 ITC.NS at ₹399.50"), startsWith("Your order"));
        Map<String, Object> row = only(3);
        assertThat(row.get("status"), is("SENT"));
        assertThat(row.get("channel"), is("EMAIL"));
        assertThat(row.get("destination"), is("r•••@example.com"));
    }

    @Test
    @DisplayName("the channel follows the preference: switched to the inbox, the next message is not mailed")
    void channelFollowsThePreference() {
        call(3, HttpMethod.PUT, "/api/v1/accounts/3/preferences",
                "{\"defaultAccountId\":3,\"landingScreen\":\"dashboard\",\"alertChannel\":\"IN_APP\"}");
        listener.onTradeEvent(tradeEvent(UUID.randomUUID(), "ORDER_REJECTED", 3, "null", "PRICE_NOT_MET"));

        dispatcher.dispatchOnce();

        verify(mail, never()).send(anyString(), anyString(), anyString());
        Map<String, Object> row = only(3);
        assertThat(row.get("status"), is("SENT"));
        assertThat(row.get("channel"), is("IN_APP"));
        assertThat(row.get("destination"), is((Object) null));
    }

    @Test
    @DisplayName("an event for an account that does not exist is refused by the database: dead-lettered, never guessed")
    void unknownAccount() {
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> listener.onTradeEvent(tradeEvent(UUID.randomUUID(), "ORDER_FILLED", 999_999, "1.00", null)));
    }

    @Test
    @DisplayName("history is the caller's own: another token is refused, and another account's message is NTF-404")
    void historyIsScoped() {
        listener.onTradeEvent(tradeEvent(UUID.randomUUID(), "ORDER_FILLED", 3, "399.50", null));
        String id = jdbc.queryForObject("SELECT notification_id::text FROM notif_notification", String.class);

        ResponseEntity<List> own = history(3, 3);
        assertThat(own.getStatusCode(), is(HttpStatus.OK));
        assertThat(own.getBody().size(), is(1));
        assertThat(((Map<?, ?>) own.getBody().get(0)).get("status"), is("QUEUED"));

        ResponseEntity<Map> refused = call(4, HttpMethod.GET, "/api/v1/accounts/3/notifications", null);
        assertThat(refused.getStatusCode(), is(HttpStatus.FORBIDDEN));
        assertThat(refused.getBody().get("errorCode"), is("ACC-403"));

        ResponseEntity<Map> notTheirs = call(4, HttpMethod.POST, "/api/v1/accounts/4/notifications/" + id + "/read", null);
        assertThat(notTheirs.getStatusCode(), is(HttpStatus.NOT_FOUND));
        assertThat(notTheirs.getBody().get("errorCode"), is("NTF-404"));
        assertThat(jdbc.queryForObject("SELECT read_at IS NULL FROM notif_notification", Boolean.class), is(true));
    }

    @Test
    @DisplayName("the bell counts what is unread, and reading one takes it off")
    void unreadAndRead() {
        listener.onTradeEvent(tradeEvent(UUID.randomUUID(), "ORDER_FILLED", 3, "399.50", null));
        listener.onTradeEvent(tradeEvent(UUID.randomUUID(), "ORDER_REJECTED", 3, "null", "PRICE_NOT_MET"));
        String id = jdbc.queryForList("SELECT notification_id::text FROM notif_notification", String.class).get(0);

        assertThat(call(3, HttpMethod.GET, "/api/v1/accounts/3/notifications/unread", null).getBody().get("unread"), is(2));
        assertThat(call(3, HttpMethod.POST, "/api/v1/accounts/3/notifications/" + id + "/read", null).getStatusCode(),
                is(HttpStatus.NO_CONTENT));
        assertThat(call(3, HttpMethod.POST, "/api/v1/accounts/3/notifications/" + id + "/read", null).getStatusCode(),
                is(HttpStatus.NO_CONTENT));
        assertThat(call(3, HttpMethod.GET, "/api/v1/accounts/3/notifications/unread", null).getBody().get("unread"), is(1));
    }

    @Test
    @DisplayName("one quote crossing two customers' alerts queues one message each; the same quote again queues none")
    void alertsAreKeyedOnTheQuoteAndTheAlert() {
        UUID quote = UUID.randomUUID();
        AlertNotice rohans = new AlertNotice(quote, 3L, 11L, "ITC.NS", AlertNotice.Direction.ABOVE,
                new BigDecimal("400"), new BigDecimal("401"), Instant.parse("2026-10-06T04:00:00Z"));
        AlertNotice someoneElses = new AlertNotice(quote, 4L, 12L, "ITC.NS", AlertNotice.Direction.ABOVE,
                new BigDecimal("400"), new BigDecimal("401"), Instant.parse("2026-10-06T04:00:00Z"));

        DeliveryReceipt first = transaction.execute(status -> alerts.deliver(rohans));
        DeliveryReceipt second = transaction.execute(status -> alerts.deliver(someoneElses));
        DeliveryReceipt replay = transaction.execute(status -> alerts.deliver(rohans));

        assertThat(first.duplicate(), is(false));
        assertThat(second.duplicate(), is(false));
        assertThat(second.notificationId(), not(first.notificationId()));
        assertThat(replay.duplicate(), is(true));
        assertThat(replay.notificationId(), is(first.notificationId()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notif_notification WHERE kind = 'PRICE_ALERT'", Integer.class),
                is(2));
    }

    @Test
    @DisplayName("an alert is queued only inside the caller's transaction, and goes when that transaction rolls back")
    void alertNeedsTheCallersTransaction() {
        AlertNotice notice = new AlertNotice(UUID.randomUUID(), 3L, 11L, "ITC.NS", AlertNotice.Direction.BELOW,
                new BigDecimal("400"), new BigDecimal("399"), Instant.parse("2026-10-06T04:00:00Z"));

        assertThrows(IllegalTransactionStateException.class, () -> alerts.deliver(notice));

        transaction.executeWithoutResult(status -> {
            alerts.deliver(notice);
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notif_notification", Integer.class), is(0));
    }

    @Test
    @DisplayName("a customer's cancel goes out on trade-events through the outbox, and that event notifies")
    void aCancelNotifies() {
        String displayed = (String) call(3, HttpMethod.POST, "/api/v1/orders", """
                {"accountId":3,"symbol":"APEX","side":"BUY","quantity":10,
                 "price":1450.00,"idempotencyKey":"notifications-cancel-01"}""").getBody().get("orderId");
        String orderId = displayed.substring("ORD-".length());

        assertThat(call(3, HttpMethod.DELETE, "/api/v1/orders/" + orderId, null).getStatusCode(), is(HttpStatus.OK));

        Map<String, Object> outbox = jdbc.queryForMap(
                "SELECT topic, message_key, envelope::text AS envelope FROM outbox_event WHERE topic = 'trade-events'");
        assertThat(outbox.get("message_key"), is("3"));

        listener.onTradeEvent((String) outbox.get("envelope"));
        Map<String, Object> row = only(3);
        assertThat(row.get("kind"), is("ORDER_CANCELLED"));
        assertThat(row.get("subject"), is("Order cancelled: buy 10 APEX"));
    }
}
