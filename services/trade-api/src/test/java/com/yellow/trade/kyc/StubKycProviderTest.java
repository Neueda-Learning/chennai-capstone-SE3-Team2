package com.yellow.trade.kyc;

import com.yellow.trade.kyc.KycProvider.Applicant;
import com.yellow.trade.kyc.KycProvider.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class StubKycProviderTest {

    private final StubKycProvider provider = new StubKycProvider();

    private static Applicant withPan(String pan) {
        return withBank(pan, "509876543210", "DEMO0000001");
    }

    private static Applicant withBank(String pan, String account, String ifsc) {
        return new Applicant(pan, "Priya Menon", LocalDate.of(1990, 5, 17), account, ifsc);
    }

    @Test
    @DisplayName("An individual's PAN the registry knows, with a bank account that verifies, passes every check")
    void individualFound() {
        Verdict verdict = provider.verify(withPan("ABCPM1234Q"));

        assertThat(verdict.passed(), is(true));
        assertThat(verdict.reason(), is(nullValue()));
        assertThat(verdict.checks(), is(Map.of("panHolderType", "pass", "registry", "pass", "bankAccount", "pass")));
    }

    @Test
    @DisplayName("A firm's PAN (fourth character F) fails, and the registry is not asked")
    void notAnIndividual() {
        Verdict verdict = provider.verify(withPan("ABCFS1234A"));

        assertThat(verdict.passed(), is(false));
        assertThat(verdict.reason(), is(StubKycProvider.NOT_AN_INDIVIDUAL));
        assertThat(verdict.checks(), is(Map.of("panHolderType", "fail")));
    }

    @Test
    @DisplayName("An individual's PAN with digits 0000 is not found at the registry")
    void notFound() {
        Verdict verdict = provider.verify(withPan("ABCPS0000A"));

        assertThat(verdict.passed(), is(false));
        assertThat(verdict.reason(), is(StubKycProvider.NOT_FOUND));
        assertThat(verdict.checks(), is(Map.of("panHolderType", "pass", "registry", "fail")));
    }

    @Test
    @DisplayName("A bank account number ending 0000 cannot be verified")
    void bankNotVerified() {
        Verdict verdict = provider.verify(withBank("ABCPM1234Q", "509876540000", "DEMO0000001"));

        assertThat(verdict.passed(), is(false));
        assertThat(verdict.reason(), is(StubKycProvider.BANK_NOT_VERIFIED));
        assertThat(verdict.checks(), is(Map.of("panHolderType", "pass", "registry", "pass", "bankAccount", "fail")));
    }

    @Test
    @DisplayName("No bank account on file fails the bank check, closed")
    void noBankAccount() {
        Verdict verdict = provider.verify(withBank("ABCPM1234Q", null, null));

        assertThat(verdict.passed(), is(false));
        assertThat(verdict.reason(), is(StubKycProvider.BANK_NOT_VERIFIED));
    }

    @Test
    @DisplayName("An applicant prints no personal data, should one ever reach a log")
    void redacted() {
        assertThat(withPan("ABCPM1234Q").toString(), is("Applicant[redacted]"));
    }
}
