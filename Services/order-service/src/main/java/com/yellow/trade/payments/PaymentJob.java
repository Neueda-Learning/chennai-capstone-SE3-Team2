package com.yellow.trade.payments;

import com.yellow.trade.mappers.PaymentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Decides PENDING transfers once they are {@code payments.delay} old -- the
 * moment a real gateway's confirmation would arrive. One transaction per
 * transfer, so one failure leaves that transfer PENDING and does not stop the
 * others; after {@code payments.max-attempts} failures it is set aside.
 *
 * Safe on several instances at once: the guarded update lets only one decide.
 */
@Component
@ConditionalOnProperty(prefix = "payments.job", name = "enabled", havingValue = "true", matchIfMissing = true)
class PaymentJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentJob.class);

    /** The log line to alert on: money no run will move until a person acts. */
    static final String PAYMENT_SET_ASIDE = "PAYMENT_SET_ASIDE";

    private final PaymentMapper payments;
    private final PaymentDecider decider;
    private final PaymentProperties properties;
    private final Clock clock;

    PaymentJob(PaymentMapper payments, PaymentDecider decider, PaymentProperties properties, Clock clock) {
        this.payments = payments;
        this.decider = decider;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${payments.job.interval-ms:2000}")
    void run() {
        Instant cutoff = clock.instant().minus(properties.delay());
        List<Long> due = payments.findDue(cutoff, properties.maxAttempts(), properties.batchSize());

        for (long transferId : due) {
            try {
                decider.decide(transferId).ifPresent(status ->
                        log.info("transfer {} decided {}", transferId, status));
            } catch (RuntimeException e) {
                failed(transferId, e);
            }
        }
    }

    /** The class only, in the log and the row: a database error's message can quote the row back. */
    private void failed(long transferId, RuntimeException e) {
        String error = e.getClass().getSimpleName();
        Integer attempts;
        try {
            attempts = payments.recordFailure(transferId, error);
        } catch (RuntimeException countFailed) {
            log.warn("transfer {} not decided, left PENDING: {} (attempt not counted: {})",
                    transferId, error, countFailed.getClass().getSimpleName());
            return;
        }
        if (attempts == null) {
            return; // decided by another run in the meantime
        }
        if (attempts >= properties.maxAttempts()) {
            log.warn("{} transfer {} set aside after {} failed attempts, left PENDING: {}. "
                            + "Fix the cause, then set fund_transfer.attempts to 0 to retry",
                    PAYMENT_SET_ASIDE, transferId, attempts, error);
        } else {
            log.warn("transfer {} not decided (attempt {} of {}), left PENDING: {}",
                    transferId, attempts, properties.maxAttempts(), error);
        }
    }
}
