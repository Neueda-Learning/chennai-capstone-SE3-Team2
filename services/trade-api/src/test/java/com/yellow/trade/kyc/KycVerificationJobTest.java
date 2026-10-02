package com.yellow.trade.kyc;

import com.yellow.enums.KycStatus;
import com.yellow.trade.mappers.KycMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class KycVerificationJobTest {

    private static final Instant NOW = Instant.parse("2026-10-02T09:00:00Z");
    private static final Instant CUTOFF = NOW.minusSeconds(30);

    private final KycMapper mapper = mock(KycMapper.class);
    private final KycDecider decider = mock(KycDecider.class);
    private final KycVerificationJob job = new KycVerificationJob(mapper, decider,
            new KycProperties("kyc-events", Duration.ofSeconds(30), 50, 5),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("Takes only checks submitted at least the delay ago and not set aside, a batch at a time")
    void onlyDueChecks() {
        when(mapper.findDue(CUTOFF, 5, 50)).thenReturn(List.of());

        job.run();

        verify(mapper).findDue(CUTOFF, 5, 50);
    }

    @Test
    @DisplayName("One customer failing is counted and does not stop the next; the log names the failure, not its message")
    void oneFailureDoesNotStopTheRest(CapturedOutput output) {
        when(mapper.findDue(CUTOFF, 5, 50)).thenReturn(List.of(11L, 12L));
        when(decider.decide(11L)).thenThrow(new IllegalStateException("Key (pan)=(ABCPM1234Q)"));
        when(decider.decide(12L)).thenReturn(Optional.of(KycStatus.VERIFIED));
        when(mapper.recordFailure(11L, "IllegalStateException")).thenReturn(2);

        job.run();

        verify(mapper).recordFailure(11L, "IllegalStateException");
        verify(decider).decide(12L);
        assertThat(output.getOut(), containsString(
                "KYC client 11 not decided (attempt 2 of 5), left PENDING: IllegalStateException"));
        assertThat(output.getOut(), containsString("KYC client 12 decided VERIFIED"));
        assertThat(output.getOut(), not(containsString("ABCPM1234Q")));
        assertThat(output.getOut(), not(containsString(KycVerificationJob.KYC_SET_ASIDE)));
    }

    @Test
    @DisplayName("The last allowed failure sets the customer aside with the line to alert on")
    void setAsideAtMaxAttempts(CapturedOutput output) {
        when(mapper.findDue(CUTOFF, 5, 50)).thenReturn(List.of(11L));
        when(decider.decide(11L)).thenThrow(new IllegalStateException("boom"));
        when(mapper.recordFailure(11L, "IllegalStateException")).thenReturn(5);

        job.run();

        assertThat(output.getOut(), containsString(
                "KYC_SET_ASIDE KYC client 11 set aside after 5 failed attempts, left PENDING: IllegalStateException"));
    }

    @Test
    @DisplayName("When the failure cannot even be counted, the job still moves on to the next customer")
    void countingFails(CapturedOutput output) {
        when(mapper.findDue(CUTOFF, 5, 50)).thenReturn(List.of(11L, 12L));
        when(decider.decide(11L)).thenThrow(new IllegalStateException("boom"));
        when(mapper.recordFailure(anyLong(), anyString()))
                .thenThrow(new DataAccessResourceFailureException("connection refused"));
        when(decider.decide(12L)).thenReturn(Optional.of(KycStatus.REJECTED));

        job.run();

        assertThat(output.getOut(), containsString(
                "KYC client 11 not decided, left PENDING: IllegalStateException "
                        + "(attempt not counted: DataAccessResourceFailureException)"));
        assertThat(output.getOut(), containsString("KYC client 12 decided REJECTED"));
    }

    @Test
    @DisplayName("A customer another run already decided is passed over without a log line")
    void alreadyDecided(CapturedOutput output) {
        when(mapper.findDue(CUTOFF, 5, 50)).thenReturn(List.of(11L));
        when(decider.decide(11L)).thenReturn(Optional.empty());

        job.run();

        verify(mapper, never()).recordFailure(anyLong(), anyString());
        assertThat(output.getOut(), not(containsString("KYC client 11")));
    }
}
