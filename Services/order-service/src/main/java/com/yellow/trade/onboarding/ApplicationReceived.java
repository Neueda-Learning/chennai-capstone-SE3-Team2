package com.yellow.trade.onboarding;

/** The only success body. It carries no identifier, so it reads the same for a new customer and a duplicate. */
public record ApplicationReceived(String status, String message) {

    static final ApplicationReceived INSTANCE = new ApplicationReceived(
            "RECEIVED",
            "Application received. If your details are verified, you will receive an email to activate your account.");
}
