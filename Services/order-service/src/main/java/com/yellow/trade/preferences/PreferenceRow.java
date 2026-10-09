package com.yellow.trade.preferences;

import java.time.Instant;

/** One pref_preference row. */
public class PreferenceRow {

    private Long clientId;
    private Long defaultAccountId;
    private String landingScreen;
    private String alertChannel;
    private Instant updatedAt;

    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }
    public Long getDefaultAccountId() { return defaultAccountId; }
    public void setDefaultAccountId(Long defaultAccountId) { this.defaultAccountId = defaultAccountId; }
    public String getLandingScreen() { return landingScreen; }
    public void setLandingScreen(String landingScreen) { this.landingScreen = landingScreen; }
    public String getAlertChannel() { return alertChannel; }
    public void setAlertChannel(String alertChannel) { this.alertChannel = alertChannel; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
