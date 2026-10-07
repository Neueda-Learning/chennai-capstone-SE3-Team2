package com.yellow.trade.preferences;

import com.yellow.trade.mappers.AccountMapper;
import com.yellow.trade.mappers.AccountRow;
import com.yellow.trade.mappers.ProfileMapper;
import com.yellow.trade.preferences.api.AlertChannel;
import com.yellow.trade.preferences.api.ChannelResolver;
import com.yellow.trade.preferences.api.ResolvedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProfileChannelResolverTest {

    private final PreferenceServiceTest.InMemoryPreferences table = new PreferenceServiceTest.InMemoryPreferences();
    private final AccountMapper accounts = mock(AccountMapper.class);
    private final ProfileMapper profiles = mock(ProfileMapper.class);
    private final ChannelResolver resolver = new ProfileChannelResolver(table, accounts, profiles);

    @BeforeEach
    void rohan() {
        when(accounts.findById(3L)).thenReturn(new AccountRow());
        when(profiles.findEmail(3L)).thenReturn("rohan.nair@example.com");
    }

    private void stored(String channel) {
        PreferenceRow row = new PreferenceRow();
        row.setClientId(3L);
        row.setDefaultAccountId(3L);
        row.setLandingScreen("dashboard");
        row.setAlertChannel(channel);
        row.setUpdatedAt(Instant.parse("2026-10-06T10:00:00Z"));
        table.upsert(row);
    }

    @Test
    @DisplayName("nothing stored: email to the profile's address, flagged as the default")
    void nothingStored() {
        ResolvedChannel resolved = resolver.resolve(3L);

        assertThat(resolved.channel(), is(AlertChannel.EMAIL));
        assertThat(resolved.destination(), is("rohan.nair@example.com"));
        assertThat(resolved.fromDefault(), is(true));
    }

    @Test
    @DisplayName("the stored channel is used, and changing it changes what resolves")
    void storedChannel() {
        stored("IN_APP");
        assertThat(resolver.resolve(3L).channel(), is(AlertChannel.IN_APP));
        assertThat(resolver.resolve(3L).destination(), is(nullValue()));

        stored("EMAIL");
        ResolvedChannel resolved = resolver.resolve(3L);
        assertThat(resolved.channel(), is(AlertChannel.EMAIL));
        assertThat(resolved.fromDefault(), is(false));
    }

    @Test
    @DisplayName("the address is read from the profile each time, never copied: a change of address is used at once")
    void addressReadEachTime() {
        when(profiles.findEmail(3L)).thenReturn("rohan@new.example.com");

        assertThat(resolver.resolve(3L).destination(), is("rohan@new.example.com"));
    }

    @Test
    @DisplayName("email chosen but no address on file: the inbox, rather than nowhere")
    void noAddress() {
        stored("EMAIL");
        when(profiles.findEmail(3L)).thenReturn(null);

        assertThat(resolver.resolve(3L).channel(), is(AlertChannel.IN_APP));
    }

    @Test
    @DisplayName("an account that does not exist is said so, not resolved to a guess")
    void unknownAccount() {
        assertThrows(ChannelResolver.UnknownAccountException.class, () -> resolver.resolve(9L));
    }

    @Test
    @DisplayName("printing a resolution never prints the address")
    void addressNotPrinted() {
        assertThat(resolver.resolve(3L).toString(), not(containsString("rohan")));
    }
}
