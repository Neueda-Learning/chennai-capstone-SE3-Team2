package com.yellow.trade.config;

public class KafkaTopics {

    public static final String ORDERS = "orders";

    /** Order outcomes. The executor publishes fills and rejections; the Trade REST API, cancellations. */
    public static final String TRADE_EVENTS = "trade-events";

    private KafkaTopics() {
        // Utility class
    }
}
