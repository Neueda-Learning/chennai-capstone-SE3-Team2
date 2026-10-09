package com.yellow.trade.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class MaskingTest {

    @Test
    @DisplayName("an email keeps its first letter and its domain, and nothing else of the name")
    void email() {
        assertThat(Masking.email("rohan.nair@example.com"), is("r•••@example.com"));
        assertThat(Masking.email("a@x.in"), is("a•••@x.in"));
    }

    @Test
    @DisplayName("something that is not an email is hidden entirely, and none stays none")
    void notAnEmail() {
        assertThat(Masking.email("not-an-email"), is("•••"));
        assertThat(Masking.email(null), is(nullValue()));
    }
}
