package com.yellow.trade.preferences;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** Where sign-in lands, spelled as the application's URL spells it. */
public enum LandingScreen {
    DASHBOARD("dashboard"),
    ORDERS("orders"),
    HOLDINGS("holdings"),
    MARKET_WATCH("market-watch");

    private final String value;

    LandingScreen(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    /** An unknown screen is refused, which the API answers as VAL-422. */
    @JsonCreator
    public static LandingScreen of(String value) {
        return Arrays.stream(values()).filter(screen -> screen.value.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no landing screen " + value));
    }
}
