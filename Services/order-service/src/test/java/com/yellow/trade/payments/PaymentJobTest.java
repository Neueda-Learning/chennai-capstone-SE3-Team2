package com.yellow.trade.payments;

import com.yellow.trade.mappers.PaymentMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class PaymentJobTest {

    private static final Instant NOW = Instant.parse("2026-10-03T10:00:00Z");
    private static final Instant CUTOFF = NOW.minusSeconds(3);

    private final PaymentMapper payments = mock(PaymentMapper.class);
    private final PaymentDecider decider = mock(PaymentDecider.class);
    private final PaymentJob job = new PaymentJob(payments, decider,
            new PaymentProperties(Duration.ofSeconds(3), 50, 5), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("Takes transfers at least the delay old, not set aside, a batch at a time")
    void onlyDue() {
        when(payments.findDue(CUTOFF, 5, 50)).thenReturn(List.of());

        job.run();

        verify(payments).findDue(CUTOFF, 5, 50);
    }

    @Test
    @DisplayName("One transfer failing is counted and does not stop the next")
    void oneFailureDoesNotStopTheRest(CapturedOutput output) {
        when(payments.findDue(CUTOFF, 5, 50)).thenReturn(List.of(7L, 8L));
        when(decider.decide(7L)).thenThrow(new IllegalStateException("boom"));
        when(decider.decide(8L)).thenReturn(Optional.of("SUCCESS"));
        when(payments.recordFailure(7L, "IllegalStateException")).thenReturn(1);

        job.run();

        verify(decider).decide(8L);
        assertThat(output.getOut(), containsString("transfer 7 not decided (attempt 1 of 5), left PENDING: IllegalStateException"));
        assertThat(output.getOut(), containsString("transfer 8 decided SUCCESS"));
    }

    @Test
    @DisplayName("The last allowed failure sets the transfer aside, with the line to alert on")
    void setAside(CapturedOutput output) {
        when(payments.findDue(CUTOFF, 5, 50)).thenReturn(List.of(7L));
        when(decider.decide(7L)).thenThrow(new IllegalStateException("boom"));
        when(payments.recordFailure(7L, "IllegalStateException")).thenReturn(5);

        job.run();

        assertThat(output.getOut(), containsString("PAYMENT_SET_ASIDE transfer 7 set aside after 5 failed attempts"));
    }
}
