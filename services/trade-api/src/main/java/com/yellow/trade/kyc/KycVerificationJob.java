package com.yellow.trade.kyc;

import com.yellow.trade.mappers.KycMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Decides pending verifications once they are {@code kyc.delay} old. One
 * transaction per customer, so one failure leaves that customer PENDING for
 * the next run and does not stop the others.
 *
 * Safe to run on several instances at once: the guarded update lets only one
 * of them decide a customer.
 */
@Component
@ConditionalOnProperty(prefix = "kyc.job", name = "enabled", havingValue = "true", matchIfMissing = true)
class KycVerificationJob {

    private static final Logger log = LoggerFactory.getLogger(KycVerificationJob.class);

    private final KycMapper mapper;
    private final KycDecider decider;
    private final KycProperties properties;
    private final Clock clock;

    KycVerificationJob(KycMapper mapper, KycDecider decider, KycProperties properties, Clock clock) {
        this.mapper = mapper;
        this.decider = decider;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${kyc.job.interval-ms:10000}")
    void run() {
        Instant cutoff = clock.instant().minus(properties.delay());
        List<Long> due = mapper.findDue(cutoff, properties.batchSize());

        for (long clientId : due) {
            try {
                decider.decide(clientId).ifPresent(status ->
                        log.info("KYC client {} decided {}", clientId, status));
            } catch (RuntimeException e) {
                // The class only: a database error's message can quote the
                // customer's data back.
                log.warn("KYC client {} not decided, left PENDING: {}",
                        clientId, e.getClass().getSimpleName());
            }
        }
    }
}
