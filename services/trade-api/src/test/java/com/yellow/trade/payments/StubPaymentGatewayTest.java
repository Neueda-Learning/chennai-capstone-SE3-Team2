package com.yellow.trade.payments;

import com.yellow.trade.payments.PaymentGateway.Decision;
import com.yellow.trade.payments.PaymentGateway.Direction;
import com.yellow.trade.payments.PaymentGateway.Instruction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;

class StubPaymentGatewayTest {

    private final StubPaymentGateway gateway = new StubPaymentGateway();

    private static Instruction of(Direction direction, String amount) {
        return new Instruction(42, direction, new BigDecimal(amount), "509876543210", "DEMO0000001", "Priya Menon");
    }

    @Test
    @DisplayName("Up to ₹2,00,000 goes through, either way, with the gateway's reference")
    void withinLimit() {
        for (Direction direction : Direction.values()) {
            Decision decision = gateway.process(of(direction, "200000.00"));

            assertThat(decision.succeeded(), is(true));
            assertThat(decision.reason(), is(nullValue()));
            assertThat(decision.reference(), is("STUB-00000042"));
        }
    }

    @Test
    @DisplayName("Over ₹2,00,000 in one transfer is declined, with the reason")
    void overLimit() {
        Decision decision = gateway.process(of(Direction.DEPOSIT, "200000.01"));

        assertThat(decision.succeeded(), is(false));
        assertThat(decision.reason(), is(StubPaymentGateway.OVER_LIMIT));
    }

    @Test
    @DisplayName("An instruction prints no bank account, should one ever reach a log")
    void redacted() {
        assertThat(of(Direction.WITHDRAWAL, "10").toString(), not(containsString("509876543210")));
    }
}
