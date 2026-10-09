package com.yellow.trade.preferences;

import com.yellow.trade.preferences.api.AlertChannel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** What Settings saves. No contact detail: it is the profile's (decision log 0003). */
public record PreferencesUpdate(@NotNull @Min(1) Long defaultAccountId,
                                @NotNull LandingScreen landingScreen,
                                @NotNull AlertChannel alertChannel) {
}
