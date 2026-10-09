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
 * the next run and does not stop the others. After {@code kyc.max-attempts}
 * failures the customer is set aside, so a check that always fails cannot
 * hold a place in every batch.
 *
 * Safe to run on several instances at once: the guarded update lets only one
 * of them decide a customer.
 */
@Component
@ConditionalOnProperty(prefix = "kyc.job", name = "enabled", havingValue = "true", matchIfMissing = true)
class KycVerificationJob {

    private static final Logger log = LoggerFactory.getLogger(KycVerificationJob.class);

    /** The log line to alert on: a customer no check will reach until a person acts. */
    static final String KYC_SET_ASIDE = "KYC_SET_ASIDE";

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
        List<Long> due = mapper.findDue(cutoff, properties.maxAttempts(), properties.batchSize());

        for (long clientId : due) {
            try {
                decider.decide(clientId).ifPresent(status ->
                        log.info("KYC client {} decided {}", clientId, status));
            } catch (RuntimeException e) {
                failed(clientId, e);
            }
        }
    }

    /** The class only, in the log and the row: a database error's message can quote the customer's data back. */
    private void failed(long clientId, RuntimeException e) {
        String error = e.getClass().getSimpleName();
        Integer attempts;
        try {
            attempts = mapper.recordFailure(clientId, error);
        } catch (RuntimeException countFailed) {
            // Uncounted, so retried next run as before. Usually the database
            // itself is down, and the next findDue fails too.
            log.warn("KYC client {} not decided, left PENDING: {} (attempt not counted: {})",
                    clientId, error, countFailed.getClass().getSimpleName());
            return;
        }
        if (attempts == null) {
            return; // decided by another run in the meantime
        }
        if (attempts >= properties.maxAttempts()) {
            log.warn("{} KYC client {} set aside after {} failed attempts, left PENDING: {}. "
                    + "Fix the cause, then set kyc_verification.attempts to 0 to retry",
                    KYC_SET_ASIDE, clientId, attempts, error);
        } else {
            log.warn("KYC client {} not decided (attempt {} of {}), left PENDING: {}",
                    clientId, attempts, properties.maxAttempts(), error);
        }
    }
}
