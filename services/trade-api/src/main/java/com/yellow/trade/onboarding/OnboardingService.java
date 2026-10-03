package com.yellow.trade.onboarding;

import com.yellow.trade.kyc.KycService;
import com.yellow.trade.mappers.OnboardingMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * Creates a customer: an ACTIVE account with KYC PENDING, their profile, their
 * bank account, and a queued verification -- all in one transaction.
 */
@Service
public class OnboardingService {

    /** A birthday falls on an Indian date, not a UTC one. */
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final OnboardingMapper mapper;
    private final KycService kyc;
    private final Clock clock;

    public OnboardingService(OnboardingMapper mapper, KycService kyc, Clock clock) {
        this.mapper = mapper;
        this.kyc = kyc;
        this.clock = clock;
    }

    /**
     * @return the new client id
     * @throws DuplicateApplicationException the PAN or the email is already
     *         registered; nothing is created
     * @throws InvalidApplicationException well formed but not acceptable
     */
    @Transactional
    public long apply(ApplicationRequest request) {
        LocalDate dob = dateOfBirth(request.dob());

        long clientId = mapper.nextClientId();
        String accountRef = "ACC-%06d".formatted(clientId);
        // NSDL style: IN, DP id 300010, an 8-digit client number. %08d never
        // truncates, so past eight digits the insert fails on the column
        // length rather than colliding with another customer.
        String dematId = "IN300010%08d".formatted(clientId);

        if (mapper.insertAccount(clientId, accountRef, request.pan(), dematId) == 0
                || mapper.insertProfile(clientId, request.name(), dob, request.email(),
                        request.phoneNumber(), request.address()) == 0) {
            // Thrown, not returned, so the account row is rolled back when the
            // profile is the duplicate.
            throw new DuplicateApplicationException();
        }
        // In the applicant's own name: payouts go only to the customer.
        mapper.insertBankAccount(clientId, request.bankAccountNumber(), request.ifsc(), request.name());

        kyc.submit(clientId);
        return clientId;
    }

    private LocalDate dateOfBirth(String value) {
        LocalDate dob;
        try {
            dob = LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new InvalidApplicationException("dob is not a real date");
        }
        if (dob.isAfter(LocalDate.now(clock.withZone(INDIA)))) {
            throw new InvalidApplicationException("dob is in the future");
        }
        return dob;
    }

    /** No detail: whatever matched is not repeated anywhere. */
    public static class DuplicateApplicationException extends RuntimeException {
        DuplicateApplicationException() {
            super("PAN or email already registered");
        }
    }

    /** The message names the field and the rule, never the value. */
    public static class InvalidApplicationException extends RuntimeException {
        InvalidApplicationException(String reason) {
            super(reason);
        }
    }
}
