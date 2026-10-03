package com.yellow.trade.onboarding;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A new customer's details. These rules check the input is well formed and
 * answer VAL-422 at once; whether the customer is eligible is KYC's decision,
 * made later.
 *
 * The bank account is the one money will move to and from. KYC checks it; the
 * number is personal data and is never logged.
 *
 * dob is a string on purpose. As a LocalDate, a malformed date would fail in
 * Jackson, and Jackson's error message -- which the global handler logs --
 * quotes the value. Parsed in {@link OnboardingService} instead, it never
 * reaches a log.
 */
public record ApplicationRequest(
        @NotBlank @Size(min = 2, max = 120) String name,
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String dob,
        @NotBlank @Email @Size(max = 200) String email,
        @NotBlank @Pattern(regexp = "\\+91[6-9]\\d{9}") String phoneNumber,
        @NotBlank @Pattern(regexp = "[A-Z]{5}[0-9]{4}[A-Z]") String pan,
        @Size(max = 500) String address,
        @NotBlank @Pattern(regexp = "[0-9]{9,18}") String bankAccountNumber,
        @NotBlank @Pattern(regexp = "[A-Z]{4}0[A-Z0-9]{6}") String ifsc) {
}
