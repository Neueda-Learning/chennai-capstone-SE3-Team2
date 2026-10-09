package com.yellow.trade.security;

/** Personal data as a screen or a record may show it. */
public final class Masking {

    private Masking() {
    }

    /** rohan.nair@example.com -> r•••@example.com. Anything else -> •••. */
    public static String email(String address) {
        if (address == null) {
            return null;
        }
        int at = address.indexOf('@');
        if (at < 1 || at == address.length() - 1) {
            return "•••";
        }
        return address.charAt(0) + "•••" + address.substring(at);
    }
}
