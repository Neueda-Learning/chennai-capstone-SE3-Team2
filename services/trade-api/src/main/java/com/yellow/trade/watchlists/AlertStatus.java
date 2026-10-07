package com.yellow.trade.watchlists;

/** ACTIVE waits for a crossing; TRIGGERED has fired and waits to be re-armed; CANCELLED, by the customer. */
public enum AlertStatus {
    ACTIVE,
    TRIGGERED,
    CANCELLED
}
