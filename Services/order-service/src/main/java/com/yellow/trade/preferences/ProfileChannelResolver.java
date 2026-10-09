package com.yellow.trade.preferences;

import com.yellow.trade.mappers.AccountMapper;
import com.yellow.trade.mappers.ProfileMapper;
import com.yellow.trade.preferences.api.AlertChannel;
import com.yellow.trade.preferences.api.ChannelResolver;
import com.yellow.trade.preferences.api.ResolvedChannel;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The implementation behind {@link ChannelResolver}: the stored channel, or the
 * documented default, with the address read from the profile at this moment
 * (decision logs 0003, 0004). No route reaches it; the notifications module
 * calls it in process.
 */
@Component
class ProfileChannelResolver implements ChannelResolver {

    private final PreferenceMapper preferences;
    private final AccountMapper accounts;
    private final ProfileMapper profiles;

    ProfileChannelResolver(PreferenceMapper preferences, AccountMapper accounts, ProfileMapper profiles) {
        this.preferences = preferences;
        this.accounts = accounts;
        this.profiles = profiles;
    }

    @Override
    @Transactional(readOnly = true)
    public ResolvedChannel resolve(long accountId) {
        if (accounts.findById(accountId) == null) {
            throw new UnknownAccountException(accountId);
        }
        PreferenceRow row = preferences.findByClient(accountId);
        AlertChannel channel = row == null ? PreferenceService.DEFAULT_CHANNEL : AlertChannel.valueOf(row.getAlertChannel());
        if (channel == AlertChannel.IN_APP) {
            return new ResolvedChannel(AlertChannel.IN_APP, null, row == null);
        }
        String address = profiles.findEmail(accountId);
        if (address == null || address.isBlank()) {
            // Email chosen, nowhere to send it: the inbox rather than nowhere.
            return new ResolvedChannel(AlertChannel.IN_APP, null, row == null);
        }
        return new ResolvedChannel(AlertChannel.EMAIL, address, row == null);
    }
}
