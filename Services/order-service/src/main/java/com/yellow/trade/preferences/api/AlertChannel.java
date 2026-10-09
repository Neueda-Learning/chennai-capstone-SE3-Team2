package com.yellow.trade.preferences.api;

/**
 * The channels a customer may choose. EMAIL also keeps the message in the
 * in-app inbox; IN_APP keeps it there only. SMS and push are not provisioned
 * on this platform.
 */
public enum AlertChannel {
    EMAIL,
    IN_APP
}
