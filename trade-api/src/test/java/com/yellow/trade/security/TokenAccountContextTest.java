package com.yellow.trade.security;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

class TokenAccountContextTest {

    @Test
    void currentAccountIdReturnsTheStandInValue() {
        TokenAccountContext context = new TokenAccountContext();

        assertThat(context.currentAccountId(), is(equalTo(1L)));
    }
}