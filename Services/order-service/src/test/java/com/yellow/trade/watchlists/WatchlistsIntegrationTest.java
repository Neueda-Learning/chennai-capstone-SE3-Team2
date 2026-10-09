package com.yellow.trade.watchlists;

import com.yellow.trade.integration.PostgresSupport;
import com.yellow.trade.notifications.NotificationDispatcher;
import com.yellow.trade.notifications.NotificationMailSender;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;

/**
 * Watchlists and alerts against the real schema: the routes, the caps, the
 * consumer's quote through the partial index, and the alert reaching the
 * real notifications module through AlertDelivery, then the customer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIf(
        value = "com.yellow.trade.integration.PostgresSupport#databaseAvailable",
        disabledReason = "needs a database: start Docker, or set IT_DB_URL "
                + "(with IT_DB_USER and IT_DB_PASSWORD) to a PostgreSQL you already have")
class WatchlistsIntegrationTest extends PostgresSupport {

    /** A tradable stock in the seed data. */
    private static final String STOCK = "APEX";

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MarketDataListener listener;
    @Autowired private NotificationDispatcher dispatcher;

    /** Nothing is ever mailed from a test. */
    @MockitoBean private NotificationMailSender mail;

    @BeforeEach
    void rebuildDatabase() {
        applySchema(jdbc);
    }

    private ResponseEntity<Map> call(long asAccount, HttpMethod method, String path, String body) {
        HttpHeaders headers = tokenFor(asAccount);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    private long createWatchlist(long account, String name) {
        ResponseEntity<Map> created = call(account, HttpMethod.POST, "/api/v1/accounts/" + account + "/watchlists",
                "{\"name\":\"" + name + "\"}");
        assertThat(created.getStatusCode(), is(HttpStatus.CREATED));
        return ((Number) created.getBody().get("id")).longValue();
    }

    private long setAlert(String direction, String threshold) {
        ResponseEntity<Map> created = call(3, HttpMethod.POST, "/api/v1/accounts/3/alerts",
                "{\"symbol\":\"" + STOCK + "\",\"direction\":\"" + direction + "\",\"threshold\":" + threshold + "}");
        assertThat(created.getStatusCode(), is(HttpStatus.CREATED));
        return ((Number) created.getBody().get("id")).longValue();
    }

    private static String quote(UUID eventId, String price, String asOf) {
        return """
                {"eventId":"%s","eventType":"QUOTE","eventTime":"%s","source":"market-poller","schemaVersion":1,
                 "payload":{"symbol":"%s","price":%s,"bid":%s,"ask":%s,"currency":"INR","change":1,"changePercent":0.4,
                            "previousClose":1490,"marketState":"open","stale":false,"quoteAsOf":"%s"}}"""
                .formatted(eventId, asOf, STOCK, price, price, price, asOf);
    }

    private Map<String, Object> alert(long id) {
        return jdbc.queryForMap("SELECT status, triggered_price, notification_id FROM watch_alert WHERE alert_id = ?", id);
    }

    @Test
    @DisplayName("a crossing quote triggers the alert, and its notification is queued in the same transaction")
    void crossingTriggers() {
        long id = setAlert("ABOVE", "1500");

        listener.onQuote(quote(UUID.randomUUID(), "1500.25", "2026-10-06T04:00:00Z"));

        Map<String, Object> fired = alert(id);
        assertThat(fired.get("status"), is("TRIGGERED"));
        assertThat((BigDecimal) fired.get("triggered_price"), comparesEqualTo(new BigDecimal("1500.25")));
        Map<String, Object> notification = jdbc.queryForMap(
                "SELECT kind, status, client_id, alert_id, subject FROM notif_notification WHERE notification_id = ?",
                fired.get("notification_id"));
        assertThat(notification.get("kind"), is("PRICE_ALERT"));
        assertThat(notification.get("status"), is("QUEUED"));
        assertThat(((Number) notification.get("alert_id")).longValue(), is(id));
        assertThat(notification.get("subject"), is("APEX rose to ₹1,500.25"));
    }

    @Test
    @DisplayName("a quote short of the threshold fires nothing; one exactly at it does, BELOW as ABOVE")
    void shortOfItDoesNot() {
        long above = setAlert("ABOVE", "1500");
        long below = setAlert("BELOW", "1400");

        listener.onQuote(quote(UUID.randomUUID(), "1499.99", "2026-10-06T04:00:00Z"));
        assertThat(alert(above).get("status"), is("ACTIVE"));
        assertThat(alert(below).get("status"), is("ACTIVE"));

        listener.onQuote(quote(UUID.randomUUID(), "1400", "2026-10-06T04:01:00Z"));
        assertThat(alert(above).get("status"), is("ACTIVE"));
        assertThat(alert(below).get("status"), is("TRIGGERED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notif_notification", Integer.class), is(1));
    }

    @Test
    @DisplayName("a replayed quote fires nothing twice: one notification")
    void replayFiresOnce() {
        setAlert("ABOVE", "1500");
        String quote = quote(UUID.randomUUID(), "1510", "2026-10-06T04:00:00Z");

        listener.onQuote(quote);
        listener.onQuote(quote);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM notif_notification", Integer.class), is(1));
    }

    @Test
    @DisplayName("delivery goes through notifications, on the customer's channel: mailed to the profile's address")
    void deliveredThroughNotifications() {
        setAlert("ABOVE", "1500");
        listener.onQuote(quote(UUID.randomUUID(), "1510", "2026-10-06T04:00:00Z"));

        dispatcher.dispatchOnce();

        verify(mail).send(eq("rohan.nair@example.com"), eq("APEX rose to ₹1,510.00"), startsWith("Your alert for APEX"));
        assertThat(jdbc.queryForObject("SELECT status FROM notif_notification", String.class), is("SENT"));
    }

    @Test
    @DisplayName("an entry shows the latest price the stream carried; a quote observed earlier does not replace it")
    void latestPriceBesideTheEntry() {
        long list = createWatchlist(3, "Long term");
        assertThat(call(3, HttpMethod.PUT, "/api/v1/accounts/3/watchlists/" + list + "/items/" + STOCK, null)
                .getStatusCode(), is(HttpStatus.NO_CONTENT));

        listener.onQuote(quote(UUID.randomUUID(), "1495.5", "2026-10-06T04:05:00Z"));
        listener.onQuote(quote(UUID.randomUUID(), "1480", "2026-10-06T04:00:00Z"));

        List<?> lists = rest.exchange("/api/v1/accounts/3/watchlists", HttpMethod.GET,
                new HttpEntity<>(tokenFor(3)), List.class).getBody();
        Map<?, ?> item = (Map<?, ?>) ((List<?>) ((Map<?, ?>) lists.get(0)).get("items")).get(0);
        assertThat(item.get("symbol"), is(STOCK));
        assertThat(new BigDecimal(item.get("lastPrice").toString()), comparesEqualTo(new BigDecimal("1495.5")));
        assertThat(item.get("priceAsOf"), is("2026-10-06T04:05:00Z"));
    }

    @Test
    @DisplayName("an entry no quote has reached yet has no price, and no watched instrument need be held")
    void noPriceYet() {
        long list = createWatchlist(3, "Curious");
        call(3, HttpMethod.PUT, "/api/v1/accounts/3/watchlists/" + list + "/items/" + STOCK, null);

        List<?> lists = rest.exchange("/api/v1/accounts/3/watchlists", HttpMethod.GET,
                new HttpEntity<>(tokenFor(3)), List.class).getBody();
        Map<?, ?> item = (Map<?, ?>) ((List<?>) ((Map<?, ?>) lists.get(0)).get("items")).get(0);
        assertThat(item.get("lastPrice"), is(nullValue()));
    }

    @Test
    @DisplayName("a watchlist is scoped to its account: another token is ACC-403, another's watchlist WCH-404")
    void scoped() {
        long theirs = createWatchlist(4, "Theirs");

        ResponseEntity<Map> read = call(4, HttpMethod.GET, "/api/v1/accounts/3/watchlists", null);
        assertThat(read.getStatusCode(), is(HttpStatus.FORBIDDEN));
        assertThat(read.getBody().get("errorCode"), is("ACC-403"));

        ResponseEntity<Map> add = call(3, HttpMethod.PUT, "/api/v1/accounts/3/watchlists/" + theirs + "/items/" + STOCK, null);
        assertThat(add.getStatusCode(), is(HttpStatus.NOT_FOUND));
        assertThat(add.getBody().get("errorCode"), is("WCH-404"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM watch_item", Integer.class), is(0));
    }

    @Test
    @DisplayName("the caps hold: a sixth watchlist and a twenty-first active alert are LIM-409")
    void capsHold() {
        for (int i = 1; i <= WatchLimits.WATCHLISTS; i++) {
            createWatchlist(3, "List " + i);
        }
        ResponseEntity<Map> sixth = call(3, HttpMethod.POST, "/api/v1/accounts/3/watchlists", "{\"name\":\"Six\"}");
        assertThat(sixth.getStatusCode(), is(HttpStatus.CONFLICT));
        assertThat(sixth.getBody().get("errorCode"), is("LIM-409"));

        for (int i = 1; i <= WatchLimits.ACTIVE_ALERTS; i++) {
            setAlert("ABOVE", String.valueOf(2000 + i));
        }
        ResponseEntity<Map> one = call(3, HttpMethod.POST, "/api/v1/accounts/3/alerts",
                "{\"symbol\":\"" + STOCK + "\",\"direction\":\"ABOVE\",\"threshold\":3000}");
        assertThat(one.getStatusCode(), is(HttpStatus.CONFLICT));
        assertThat(one.getBody().get("errorCode"), is("LIM-409"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM watch_alert", Integer.class), is(WatchLimits.ACTIVE_ALERTS));
    }

    @Test
    @DisplayName("a fired alert reads TRIGGERED with its notification, and re-arming makes it fire on the next crossing")
    void rearmFiresAgain() {
        long id = setAlert("ABOVE", "1500");
        listener.onQuote(quote(UUID.randomUUID(), "1510", "2026-10-06T04:00:00Z"));
        assertThat(alert(id).get("notification_id"), is(notNullValue()));

        ResponseEntity<Map> rearmed = call(3, HttpMethod.POST, "/api/v1/accounts/3/alerts/" + id + "/rearm", null);
        assertThat(rearmed.getBody().get("status"), is("ACTIVE"));
        listener.onQuote(quote(UUID.randomUUID(), "1520", "2026-10-06T04:01:00Z"));

        assertThat(alert(id).get("status"), is("TRIGGERED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notif_notification", Integer.class), is(2));
    }

    @Test
    @DisplayName("the poller's view lists every watched instrument and every one with an ACTIVE alert, and nothing else")
    void polledSymbolsView() {
        long list = createWatchlist(3, "Watching");
        call(3, HttpMethod.PUT, "/api/v1/accounts/3/watchlists/" + list + "/items/" + STOCK, null);
        assertThat(jdbc.queryForList("SELECT symbol FROM watch_polled_symbols", String.class), is(List.of(STOCK)));

        call(3, HttpMethod.DELETE, "/api/v1/accounts/3/watchlists/" + list, null);
        assertThat(jdbc.queryForList("SELECT symbol FROM watch_polled_symbols", String.class), is(List.of()));

        long id = setAlert("BELOW", "100");
        assertThat(jdbc.queryForList("SELECT symbol FROM watch_polled_symbols", String.class), is(List.of(STOCK)));
        call(3, HttpMethod.DELETE, "/api/v1/accounts/3/alerts/" + id, null);
        assertThat(jdbc.queryForList("SELECT symbol FROM watch_polled_symbols", String.class), is(List.of()));
    }
}
