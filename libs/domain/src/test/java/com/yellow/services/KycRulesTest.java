package com.yellow.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class KycRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);

    @Test
    @DisplayName("Eighteen today is an adult")
    void eighteenToday() {
        assertThat(KycRules.isAdult(LocalDate.of(2008, 10, 2), TODAY), is(true));
    }

    @Test
    @DisplayName("Eighteen tomorrow is not yet an adult")
    void eighteenTomorrow() {
        assertThat(KycRules.isAdult(LocalDate.of(2008, 10, 3), TODAY), is(false));
    }

    @Test
    @DisplayName("Well over eighteen is an adult")
    void wellOver() {
        assertThat(KycRules.isAdult(LocalDate.of(1990, 5, 17), TODAY), is(true));
    }

    @Test
    @DisplayName("Seventeen is not an adult")
    void seventeen() {
        assertThat(KycRules.isAdult(LocalDate.of(2009, 1, 1), TODAY), is(false));
    }
}
