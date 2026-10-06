package com.yellow.trade.preferences.api;

/**
 * Where a message to one customer goes.
 *
 * @param channel     the channel to send on
 * @param destination the email address for EMAIL; null for IN_APP. Personal
 *                    data: never logged, and stored by a caller only masked
 * @param fromDefault true when the customer has stored no preference and this
 *                    is the documented default
 */
public record ResolvedChannel(AlertChannel channel, String destination, boolean fromDefault) {

    @Override
    public String toString() {
        return "ResolvedChannel[channel=" + channel + ", fromDefault=" + fromDefault + "]";
    }
}
