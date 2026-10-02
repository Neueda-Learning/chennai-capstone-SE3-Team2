package com.yellow.trade.kyc;

import com.yellow.enums.KycStatus;
import com.yellow.trade.mappers.KycMapper;
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
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class KycVerificationJobTest {

    private static final Instant NOW = Instant.parse("2026-10-02T09:00:00Z");

    private final KycMapper mapper = mock(KycMapper.class);
    private final KycDecider decider = mock(KycDecider.class);
    private final KycVerificationJob job = new KycVerificationJob(mapper, decider,
            new KycProperties("kyc-events", Duration.ofSeconds(30), 50),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("Takes only checks submitted at least the delay ago, a batch at a time")
    void onlyDueChecks() {
        when(mapper.findDue(NOW.minusSeconds(30), 50)).thenReturn(List.of());

        job.run();

        verify(mapper).findDue(NOW.minusSeconds(30), 50);
    }

    @Test
    @DisplayName("One customer failing does not stop the next, and the log names the failure, not its message")
    void oneFailureDoesNotStopTheRest(CapturedOutput output) {
        when(mapper.findDue(NOW.minusSeconds(30), 50)).thenReturn(List.of(11L, 12L));
        when(decider.decide(11L)).thenThrow(new IllegalStateException("Key (pan)=(ABCPM1234Q)"));
        when(decider.decide(12L)).thenReturn(Optional.of(KycStatus.VERIFIED));

        job.run();

        verify(decider).decide(12L);
        assertThat(output.getOut(), containsString("KYC client 11 not decided, left PENDING: IllegalStateException"));
        assertThat(output.getOut(), containsString("KYC client 12 decided VERIFIED"));
        assertThat(output.getOut(), not(containsString("ABCPM1234Q")));
    }

    @Test
    @DisplayName("A customer another run already decided is passed over without a log line")
    void alreadyDecided(CapturedOutput output) {
        when(mapper.findDue(NOW.minusSeconds(30), 50)).thenReturn(List.of(11L));
        when(decider.decide(11L)).thenReturn(Optional.empty());

        job.run();

        assertThat(output.getOut(), not(containsString("KYC client 11")));
    }
}
