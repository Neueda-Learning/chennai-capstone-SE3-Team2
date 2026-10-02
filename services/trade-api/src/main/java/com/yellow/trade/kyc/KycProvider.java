package com.yellow.trade.kyc;

import java.time.LocalDate;
import java.util.Map;

/**
 * Where a real KYC vendor -- a KRA, in India -- plugs in. Only what such a
 * vendor's PAN verification takes is passed: the PAN, the name and the date of
 * birth. Nothing else about the customer leaves the platform.
 */
interface KycProvider {

    Verdict verify(Applicant applicant);

    record Applicant(String pan, String name, LocalDate dateOfBirth) {
    }

    /**
     * @param checks which checks ran and how each went, for the audit trail
     * @param reason why it failed; null when it passed
     */
    record Verdict(boolean passed, String reason, Map<String, String> checks) {

        static Verdict pass(Map<String, String> checks) {
            return new Verdict(true, null, checks);
        }

        static Verdict fail(String reason, Map<String, String> checks) {
            return new Verdict(false, reason, checks);
        }
    }
}
