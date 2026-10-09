package com.yellow.trade.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the relay. A fixed delay rather than a fixed rate: a slow broker
 * cannot stack passes on top of each other.
 */
@Component
@ConditionalOnProperty(prefix = "outbox.relay", name = "enabled", havingValue = "true", matchIfMissing = true)
class OutboxRelayJob {

    private final OutboxRelay relay;

    OutboxRelayJob(OutboxRelay relay) {
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.interval-ms:2000}")
    void run() {
        relay.relayOnce();
    }
}
