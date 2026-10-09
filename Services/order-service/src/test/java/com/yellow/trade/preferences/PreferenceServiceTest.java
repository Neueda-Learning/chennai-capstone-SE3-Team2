package com.yellow.trade.preferences;

import com.yellow.exceptions.AccountNotFoundException;
import com.yellow.trade.mappers.ProfileMapper;
import com.yellow.trade.preferences.api.AlertChannel;
import com.yellow.trade.security.AccountAccess;
import com.yellow.trade.security.AccountNotReachableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PreferenceServiceTest {

    private static final long ACCOUNT = 3L;
    private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");

    /** The table, in memory: what was saved is what is read. */
    static final class InMemoryPreferences implements PreferenceMapper {
        final Map<Long, PreferenceRow> rows = new HashMap<>();

        @Override
        public PreferenceRow findByClient(long clientId) {
            return rows.get(clientId);
        }

        @Override
        public void upsert(PreferenceRow row) {
            rows.put(row.getClientId(), row);
        }
    }

    private final InMemoryPreferences table = new InMemoryPreferences();
    private final AccountAccess access = mock(AccountAccess.class);
    private final ProfileMapper profiles = mock(ProfileMapper.class);
    private final PreferenceService service =
            new PreferenceService(table, access, profiles, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void rohanWithAnAddress() {
        when(profiles.findEmail(ACCOUNT)).thenReturn("rohan.nair@example.com");
    }

    @Test
    @DisplayName("with nothing saved: the documented defaults, said to be defaults")
    void defaults() {
        Preferences preferences = service.get(ACCOUNT);

        assertThat(preferences.stored(), is(false));
        assertThat(preferences.defaultAccountId(), is(ACCOUNT));
        assertThat(preferences.landingScreen(), is(LandingScreen.DASHBOARD));
        assertThat(preferences.alertChannel(), is(AlertChannel.EMAIL));
        assertThat(preferences.contact(), is("r•••@example.com"));
    }

    @Test
    @DisplayName("saved, then read back as saved")
    void savedAndReadBack() {
        service.save(ACCOUNT, new PreferencesUpdate(ACCOUNT, LandingScreen.MARKET_WATCH, AlertChannel.IN_APP));

        Preferences preferences = service.get(ACCOUNT);
        assertThat(preferences.stored(), is(true));
        assertThat(preferences.landingScreen(), is(LandingScreen.MARKET_WATCH));
        assertThat(preferences.alertChannel(), is(AlertChannel.IN_APP));
        assertThat(preferences.updatedAt(), is(NOW));
        // IN_APP goes nowhere outside the platform: no contact to show.
        assertThat(preferences.contact(), is((String) null));
    }

    @Test
    @DisplayName("another account's preferences are refused, never answered empty")
    void anotherAccountRefused() {
        doThrow(new AccountNotReachableException()).when(access).requireOwn(4L);

        assertThrows(AccountNotReachableException.class, () -> service.get(4L));
        assertThrows(AccountNotReachableException.class,
                () -> service.save(4L, new PreferencesUpdate(4L, LandingScreen.ORDERS, AlertChannel.EMAIL)));
    }

    @Test
    @DisplayName("a default account that is not the caller's own is refused, and nothing is saved")
    void defaultAccountMustBeOwn() {
        assertThrows(AccountNotReachableException.class,
                () -> service.save(ACCOUNT, new PreferencesUpdate(4L, LandingScreen.ORDERS, AlertChannel.EMAIL)));

        assertThat(table.rows.isEmpty(), is(true));
    }

    @Test
    @DisplayName("an account that does not exist is ACC-404")
    void unknownAccount() {
        doThrow(new AccountNotFoundException(9L)).when(access).requireOwn(9L);

        assertThrows(AccountNotFoundException.class, () -> service.get(9L));
    }
}
