package com.yellow.trade.preferences;

import com.yellow.trade.mappers.ProfileMapper;
import com.yellow.trade.preferences.api.AlertChannel;
import com.yellow.trade.security.AccountAccess;
import com.yellow.trade.security.AccountNotReachableException;
import com.yellow.trade.security.Masking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * A customer's preferences: read, with the documented defaults when nothing
 * is stored (decision log 0004), and saved. Every call is the caller's own
 * account only.
 */
@Service
public class PreferenceService {

    private static final Logger log = LoggerFactory.getLogger(PreferenceService.class);

    static final LandingScreen DEFAULT_LANDING = LandingScreen.DASHBOARD;
    static final AlertChannel DEFAULT_CHANNEL = AlertChannel.EMAIL;

    private final PreferenceMapper preferences;
    private final AccountAccess access;
    private final ProfileMapper profiles;
    private final Clock clock;

    public PreferenceService(PreferenceMapper preferences, AccountAccess access, ProfileMapper profiles, Clock clock) {
        this.preferences = preferences;
        this.access = access;
        this.profiles = profiles;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Preferences get(long accountId) {
        access.requireOwn(accountId);
        return view(accountId, preferences.findByClient(accountId));
    }

    /**
     * The default account must be the caller's own: with one account per login,
     * exactly the one addressed (decision log 0005).
     */
    @Transactional
    public Preferences save(long accountId, PreferencesUpdate update) {
        access.requireOwn(accountId);
        if (update.defaultAccountId() != accountId) {
            log.warn("ACC-403: account {} asked for account {} as its default", accountId, update.defaultAccountId());
            throw new AccountNotReachableException();
        }
        PreferenceRow row = new PreferenceRow();
        row.setClientId(accountId);
        row.setDefaultAccountId(update.defaultAccountId());
        row.setLandingScreen(update.landingScreen().value());
        row.setAlertChannel(update.alertChannel().name());
        row.setUpdatedAt(Instant.now(clock));
        preferences.upsert(row);
        return view(accountId, row);
    }

    private Preferences view(long accountId, PreferenceRow row) {
        AlertChannel channel = row == null ? DEFAULT_CHANNEL : AlertChannel.valueOf(row.getAlertChannel());
        String contact = channel == AlertChannel.EMAIL ? Masking.email(profiles.findEmail(accountId)) : null;
        if (row == null) {
            return new Preferences(accountId, accountId, DEFAULT_LANDING, channel, contact, false, null);
        }
        return new Preferences(accountId, row.getDefaultAccountId(), LandingScreen.of(row.getLandingScreen()), channel,
                contact, true, row.getUpdatedAt());
    }
}
