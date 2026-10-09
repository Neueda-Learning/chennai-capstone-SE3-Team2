package com.yellow.trade.kyc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yellow.enums.KycStatus;
import com.yellow.services.KycRules;
import com.yellow.trade.events.EventEnvelope;
import com.yellow.trade.kyc.KycProvider.Applicant;
import com.yellow.trade.kyc.KycProvider.Verdict;
import com.yellow.trade.mappers.ApplicantRow;
import com.yellow.trade.mappers.KycMapper;
import com.yellow.trade.mappers.OutboxMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs the checks for one customer and records the outcome. Everything it
 * writes -- the verification, the account's kyc_status and, on a pass, the
 * KYC_VERIFIED event -- commits together or not at all.
 */
@Service
class KycDecider {

    static final String UNDER_AGE = "under " + KycRules.MINIMUM_AGE;
    static final String EVENT_TYPE = "KYC_VERIFIED";
    static final String SOURCE = "kyc-service";
    static final int SCHEMA_VERSION = 1;

    /** A birthday falls on an Indian date, not a UTC one. */
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final KycMapper kyc;
    private final OutboxMapper outbox;
    private final KycProvider provider;
    private final KycProperties properties;
    private final ObjectMapper json;
    private final Clock clock;

    KycDecider(KycMapper kyc, OutboxMapper outbox, KycProvider provider,
               KycProperties properties, ObjectMapper json, Clock clock) {
        this.kyc = kyc;
        this.outbox = outbox;
        this.provider = provider;
        this.properties = properties;
        this.json = json;
        this.clock = clock;
    }

    /**
     * The stub answers at once, so the provider is called inside the
     * transaction. A real vendor's network call would move out of it: the
     * guarded update below already makes deciding on a stale read safe.
     *
     * @return the decision, or empty when another run decided this customer
     *         first and nothing was written
     */
    @Transactional
    Optional<KycStatus> decide(long clientId) {
        ApplicantRow applicant = kyc.findApplicant(clientId);
        if (applicant == null) {
            throw new IllegalStateException("no account or profile for client " + clientId);
        }

        Verdict verdict = check(applicant);
        KycStatus status = verdict.passed() ? KycStatus.VERIFIED : KycStatus.REJECTED;

        if (kyc.decide(clientId, status.name(), verdict.reason(), write(verdict.checks())) == 0) {
            return Optional.empty();
        }
        // Onboarding creates the account PENDING and only this writes it, so 0
        // rows means the two tables disagree. Throwing rolls the decision back
        // and leaves the customer PENDING for someone to look at.
        if (kyc.setAccountKycStatus(clientId, status.name()) == 0) {
            throw new IllegalStateException("client " + clientId + " account is no longer KYC PENDING");
        }
        if (status == KycStatus.VERIFIED) {
            queueVerifiedEvent(clientId);
        }
        return Optional.of(status);
    }

    /** Age first, then the provider. Stops at the first failure. */
    private Verdict check(ApplicantRow applicant) {
        Map<String, String> checks = new LinkedHashMap<>();

        boolean adult = KycRules.isAdult(applicant.getDob(), LocalDate.now(clock.withZone(INDIA)));
        checks.put("age", adult ? "pass" : "fail");
        if (!adult) {
            // Not sent to the provider: nothing about a customer we cannot
            // accept leaves the platform.
            return Verdict.fail(UNDER_AGE, checks);
        }

        Verdict vendor = provider.verify(new Applicant(applicant.getPan(), applicant.getName(),
                applicant.getDob(), applicant.getBankAccountNumber(), applicant.getIfsc()));
        checks.putAll(vendor.checks());
        return new Verdict(vendor.passed(), vendor.reason(), checks);
    }

    /** Contracts/API Schemas/kafka-topics.md: payload clientId only, keyed by clientId as a string. */
    private void queueVerifiedEvent(long clientId) {
        String eventId = UUID.randomUUID().toString();
        EventEnvelope<Map<String, Long>> envelope = new EventEnvelope<>(
                eventId, EVENT_TYPE, clock.instant(), SOURCE, SCHEMA_VERSION,
                Map.of("clientId", clientId));
        outbox.insert(eventId, properties.topic(), Long.toString(clientId), write(envelope));
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not write KYC JSON", e);
        }
    }
}
