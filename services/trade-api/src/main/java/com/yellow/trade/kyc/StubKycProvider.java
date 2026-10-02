package com.yellow.trade.kyc;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stands in for a KYC vendor, the way Fauxnance stands in for a market-data
 * one. Deterministic, so a demonstration can produce any outcome on demand:
 *
 * <ul>
 *   <li>PAN holder type -- the fourth character. {@code P} is an individual;
 *       {@code C} a company, {@code F} a firm, and so on. Only individuals may
 *       open this account.</li>
 *   <li>Registry -- a PAN whose four digits are {@code 0000} is treated as not
 *       found, so the vendor can be seen saying no to otherwise valid data.</li>
 * </ul>
 *
 * Checks stop at the first failure, as a vendor's would.
 */
@Component
class StubKycProvider implements KycProvider {

    static final String NOT_AN_INDIVIDUAL = "PAN is not an individual's";
    static final String NOT_FOUND = "PAN not found at the registry";

    @Override
    public Verdict verify(Applicant applicant) {
        Map<String, String> checks = new LinkedHashMap<>();
        String pan = applicant.pan();

        boolean individual = pan.charAt(3) == 'P';
        checks.put("panHolderType", individual ? "pass" : "fail");
        if (!individual) {
            return Verdict.fail(NOT_AN_INDIVIDUAL, checks);
        }

        boolean found = !pan.substring(5, 9).equals("0000");
        checks.put("registry", found ? "pass" : "fail");
        if (!found) {
            return Verdict.fail(NOT_FOUND, checks);
        }
        return Verdict.pass(checks);
    }
}
