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
        return new Applicant(pan, "Priya Menon", LocalDate.of(1990, 5, 17));
    }

    @Test
    @DisplayName("An individual's PAN the registry knows passes both checks")
    void individualFound() {
        Verdict verdict = provider.verify(withPan("ABCPM1234Q"));

        assertThat(verdict.passed(), is(true));
        assertThat(verdict.reason(), is(nullValue()));
        assertThat(verdict.checks(), is(Map.of("panHolderType", "pass", "registry", "pass")));
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
}
