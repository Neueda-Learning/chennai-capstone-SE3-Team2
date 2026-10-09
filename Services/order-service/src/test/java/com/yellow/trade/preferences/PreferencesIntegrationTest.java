package com.yellow.trade.preferences;

import com.yellow.trade.integration.PostgresSupport;
import com.yellow.trade.mappers.AccountMapper;
import com.yellow.trade.mappers.ProfileMapper;
import com.yellow.trade.preferences.api.AlertChannel;
import com.yellow.trade.preferences.api.ChannelResolver;
import com.yellow.trade.security.AccountAccess;
import com.yellow.trade.security.CallerAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Preferences over HTTP against the real schema: the token filter, the routes, the SQL. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIf(
        value = "com.yellow.trade.integration.PostgresSupport#databaseAvailable",
        disabledReason = "needs a database: start Docker, or set IT_DB_URL "
                + "(with IT_DB_USER and IT_DB_PASSWORD) to a PostgreSQL you already have")
class PreferencesIntegrationTest extends PostgresSupport {

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PreferenceMapper mapper;
    @Autowired private AccountMapper accounts;
    @Autowired private ProfileMapper profiles;
    @Autowired private ChannelResolver resolver;

    @BeforeEach
    void rebuildDatabase() {
        applySchema(jdbc);
    }

    private ResponseEntity<Map> call(long asAccount, HttpMethod method, String path, String body) {
        HttpHeaders headers = tokenFor(asAccount);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    @Test
    @DisplayName("saved, and read back over the API")
    void savedAndReadBack() {
        ResponseEntity<Map> saved = call(3, HttpMethod.PUT, "/api/v1/accounts/3/preferences",
                "{\"defaultAccountId\":3,\"landingScreen\":\"holdings\",\"alertChannel\":\"IN_APP\"}");
        assertThat(saved.getStatusCode(), is(HttpStatus.OK));

        Map<?, ?> read = call(3, HttpMethod.GET, "/api/v1/accounts/3/preferences", null).getBody();
        assertThat(read.get("landingScreen"), is("holdings"));
        assertThat(read.get("alertChannel"), is("IN_APP"));
        assertThat(read.get("stored"), is(true));
    }

    @Test
    @DisplayName("they survive a restart: a row in Postgres, which a brand-new service reads back")
    void surviveARestart() {
        call(3, HttpMethod.PUT, "/api/v1/accounts/3/preferences",
                "{\"defaultAccountId\":3,\"landingScreen\":\"orders\",\"alertChannel\":\"EMAIL\"}");

        assertThat(jdbc.queryForObject("SELECT landing_screen FROM pref_preference WHERE client_id = 3", String.class),
                is("orders"));
        // Nothing of the running service's memory: new objects over the same database.
        CallerAccount caller = mock(CallerAccount.class);
        when(caller.canReach(3L)).thenReturn(true);
        PreferenceService restarted = new PreferenceService(mapper, new AccountAccess(caller, accounts), profiles,
                Clock.systemUTC());
        assertThat(restarted.get(3L).landingScreen(), is(LandingScreen.ORDERS));
    }

    @Test
    @DisplayName("with nothing saved the defaults answer, said to be defaults, with the profile's address masked")
    void defaults() {
        Map<?, ?> read = call(3, HttpMethod.GET, "/api/v1/accounts/3/preferences", null).getBody();

        assertThat(read.get("stored"), is(false));
        assertThat(read.get("landingScreen"), is("dashboard"));
        assertThat(read.get("alertChannel"), is("EMAIL"));
        assertThat(read.get("contact"), is("r•••@example.com"));
    }

    @Test
    @DisplayName("another account's token is refused 403 ACC-403, reading or writing")
    void anotherAccount() {
        ResponseEntity<Map> read = call(4, HttpMethod.GET, "/api/v1/accounts/3/preferences", null);
        ResponseEntity<Map> write = call(4, HttpMethod.PUT, "/api/v1/accounts/3/preferences",
                "{\"defaultAccountId\":3,\"landingScreen\":\"orders\",\"alertChannel\":\"EMAIL\"}");

        assertThat(read.getStatusCode(), is(HttpStatus.FORBIDDEN));
        assertThat(read.getBody().get("errorCode"), is("ACC-403"));
        assertThat(write.getStatusCode(), is(HttpStatus.FORBIDDEN));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pref_preference", Integer.class), is(0));
    }

    @Test
    @DisplayName("the resolver the notifications module calls reads the stored channel and the profile's address")
    void resolver() {
        assertThat(resolver.resolve(3L).channel(), is(AlertChannel.EMAIL));
        assertThat(resolver.resolve(3L).destination(), is("rohan.nair@example.com"));

        call(3, HttpMethod.PUT, "/api/v1/accounts/3/preferences",
                "{\"defaultAccountId\":3,\"landingScreen\":\"orders\",\"alertChannel\":\"IN_APP\"}");
        assertThat(resolver.resolve(3L).channel(), is(AlertChannel.IN_APP));
    }

    @Test
    @DisplayName("the database itself refuses a default account that is not the customer's own")
    void checkConstraint() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO pref_preference (client_id, default_account_id, landing_screen, alert_channel) "
                        + "VALUES (3, 4, 'orders', 'EMAIL')"));
    }
}
