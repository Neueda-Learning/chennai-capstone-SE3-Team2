package com.yellow.trade.config;

/**
 * Kafka topic constants used across the trade API.
 */
public class KafkaTopics {

    /**
     * Topic name for order placement events.
     * Messages are keyed by account ID to ensure ordering per account.
     */
    public static final String ORDERS = "orders";

    private KafkaTopics() {
        // Utility class
    }
}
