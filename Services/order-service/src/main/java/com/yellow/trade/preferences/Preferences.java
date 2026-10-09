package com.yellow.trade.preferences;

import com.yellow.trade.preferences.api.AlertChannel;

import java.time.Instant;

/**
 * The preferences in force for one customer: stored, or the documented
 * defaults.
 *
 * @param contact where EMAIL goes, masked; null for IN_APP
 * @param stored  false while these are the defaults
 */
public record Preferences(long accountId, long defaultAccountId, LandingScreen landingScreen,
                          AlertChannel alertChannel, String contact, boolean stored, Instant updatedAt) {
}
