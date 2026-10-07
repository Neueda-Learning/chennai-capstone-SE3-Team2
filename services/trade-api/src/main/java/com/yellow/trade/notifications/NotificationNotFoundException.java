package com.yellow.trade.notifications;

import com.yellow.exceptions.TradeException;

/**
 * No such notification on this account: 404 NTF-404, the one code this
 * module adds to the catalogue (openapi/notifications.yaml). Another account's
 * notification answers the same, so its id says nothing about whether it exists.
 */
public class NotificationNotFoundException extends TradeException {

    public static final String CODE = "NTF-404";

    public NotificationNotFoundException() {
        super(CODE, "Notification not found");
    }
}
