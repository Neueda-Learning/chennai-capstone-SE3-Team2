package com.yellow.services;

import java.time.LocalDate;
import java.util.Objects;

/**
 * KYC eligibility rules that belong to us rather than to a KYC provider, so
 * they stay when a vendor replaces the stub.
 *
 * Pure functions: the caller supplies today's date, in the customer's
 * timezone, so the domain needs no clock.
 */
public final class KycRules {

    public static final int MINIMUM_AGE = 18;

    private KycRules() {
    }

    /** True from the eighteenth birthday onwards. */
    public static boolean isAdult(LocalDate dateOfBirth, LocalDate today) {
        Objects.requireNonNull(dateOfBirth, "dateOfBirth");
        Objects.requireNonNull(today, "today");
        return !dateOfBirth.plusYears(MINIMUM_AGE).isAfter(today);
    }
}
