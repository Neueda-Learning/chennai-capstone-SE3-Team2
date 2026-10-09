package com.yellow.trade.kyc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yellow.enums.KycStatus;
import com.yellow.trade.mappers.ApplicantRow;
import com.yellow.trade.mappers.KycMapper;
import com.yellow.trade.mappers.OutboxMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KycDeciderTest {

    /** 21:00 UTC on 1 Oct is 02:30 IST on 2 Oct: the UTC date is a day behind. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T21:00:00Z"), ZoneOffset.UTC);
    private static final long CLIENT = 11L;

    private final ObjectMapper json = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    private final KycMapper kyc = mock(KycMapper.class);
    private final OutboxMapper outbox = mock(OutboxMapper.class);
    private final KycProvider provider = spy(new StubKycProvider());
    private final KycDecider decider = new KycDecider(kyc, outbox, provider,
            new KycProperties("kyc-events", Duration.ofSeconds(30), 50, 5), json, CLOCK);

    private void applicant(String pan, LocalDate dob) {
        applicant(pan, dob, "509876543210");
    }

    private void applicant(String pan, LocalDate dob, String bankAccount) {
        ApplicantRow row = new ApplicantRow();
        row.setPan(pan);
        row.setName("Priya Menon");
        row.setDob(dob);
        row.setBankAccountNumber(bankAccount);
        row.setIfsc("DEMO0000001");
        when(kyc.findApplicant(CLIENT)).thenReturn(row);
    }

    @BeforeEach
    void stillPending() {
        when(kyc.decide(anyLong(), anyString(), any(), anyString())).thenReturn(1);
        when(kyc.setAccountKycStatus(anyLong(), anyString())).thenReturn(1);
    }

    @Test
    @DisplayName("A clean application is VERIFIED, with every check recorded and the account flipped")
    void cleanApplication() {
        applicant("ABCPM1234Q", LocalDate.of(1990, 5, 17));

        assertThat(decider.decide(CLIENT), is(Optional.of(KycStatus.VERIFIED)));

        verify(kyc).decide(CLIENT, "VERIFIED", null,
                "{\"age\":\"pass\",\"panHolderType\":\"pass\",\"registry\":\"pass\",\"bankAccount\":\"pass\"}");
        verify(kyc).setAccountKycStatus(CLIENT, "VERIFIED");
    }

    @Test
    @DisplayName("Eighteen today in India is VERIFIED, though in UTC the birthday is tomorrow")
    void eighteenToday() {
        applicant("ABCPM1234Q", LocalDate.of(2008, 10, 2));

        assertThat(decider.decide(CLIENT), is(Optional.of(KycStatus.VERIFIED)));
    }

    @Test
    @DisplayName("Seventeen is REJECTED \"under 18\", and nothing is sent to the provider")
    void underAge() {
        applicant("ABCPM1234Q", LocalDate.of(2008, 10, 3));

        assertThat(decider.decide(CLIENT), is(Optional.of(KycStatus.REJECTED)));

        verify(kyc).decide(CLIENT, "REJECTED", "under 18", "{\"age\":\"fail\"}");
        verify(kyc).setAccountKycStatus(CLIENT, "REJECTED");
        verifyNoInteractions(provider);
    }

    @Test
    @DisplayName("A firm's PAN is REJECTED as not an individual's")
    void notAnIndividual() {
        applicant("ABCFS1234A", LocalDate.of(1990, 5, 17));

        assertThat(decider.decide(CLIENT), is(Optional.of(KycStatus.REJECTED)));

        verify(kyc).decide(CLIENT, "REJECTED", StubKycProvider.NOT_AN_INDIVIDUAL,
                "{\"age\":\"pass\",\"panHolderType\":\"fail\"}");
    }

    @Test
    @DisplayName("A PAN the registry does not know is REJECTED as not found")
    void notFound() {
        applicant("ABCPS0000A", LocalDate.of(1990, 5, 17));

        assertThat(decider.decide(CLIENT), is(Optional.of(KycStatus.REJECTED)));

        verify(kyc).decide(CLIENT, "REJECTED", StubKycProvider.NOT_FOUND,
                "{\"age\":\"pass\",\"panHolderType\":\"pass\",\"registry\":\"fail\"}");
    }

    @Test
    @DisplayName("A bank account that cannot be verified is REJECTED, and queues no event")
    void bankNotVerified() {
        applicant("ABCPM1234Q", LocalDate.of(1990, 5, 17), "509876540000");

        assertThat(decider.decide(CLIENT), is(Optional.of(KycStatus.REJECTED)));

        verify(kyc).decide(CLIENT, "REJECTED", StubKycProvider.BANK_NOT_VERIFIED,
                "{\"age\":\"pass\",\"panHolderType\":\"pass\",\"registry\":\"pass\",\"bankAccount\":\"fail\"}");
        verifyNoInteractions(outbox);
    }

    @Test
    @DisplayName("VERIFIED queues exactly one KYC_VERIFIED event, keyed and shaped as the contract says")
    void verifiedQueuesOneEvent() throws Exception {
        applicant("ABCPM1234Q", LocalDate.of(1990, 5, 17));

        decider.decide(CLIENT);

        ArgumentCaptor<String> eventId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> envelope = ArgumentCaptor.forClass(String.class);
        verify(outbox).insert(eventId.capture(), eq("kyc-events"), eq("11"), envelope.capture());

        JsonNode sent = json.readTree(envelope.getValue());
        assertThat(sent.size(), is(6));
        assertThat(sent.get("eventId").asText(), is(eventId.getValue()));
        UUID.fromString(eventId.getValue());
        assertThat(sent.get("eventType").asText(), is("KYC_VERIFIED"));
        assertThat(sent.get("eventTime").asText(), is("2026-10-01T21:00:00Z"));
        assertThat(sent.get("source").asText(), is("kyc-service"));
        assertThat(sent.get("schemaVersion").asInt(), is(1));
        // The only field, and a number: auth refuses a string clientId.
        assertThat(sent.get("payload").size(), is(1));
        assertThat(sent.get("payload").get("clientId").isIntegralNumber(), is(true));
        assertThat(sent.get("payload").get("clientId").asLong(), is(CLIENT));
    }

    @Test
    @DisplayName("REJECTED queues no event")
    void rejectedQueuesNothing() {
        applicant("ABCPS0000A", LocalDate.of(1990, 5, 17));

        decider.decide(CLIENT);

        verifyNoInteractions(outbox);
    }

    @Test
    @DisplayName("Already decided by another run: nothing else is written")
    void alreadyDecided() {
        applicant("ABCPM1234Q", LocalDate.of(1990, 5, 17));
        when(kyc.decide(anyLong(), anyString(), any(), anyString())).thenReturn(0);

        assertThat(decider.decide(CLIENT), is(Optional.empty()));

        verify(kyc, never()).setAccountKycStatus(anyLong(), anyString());
        verifyNoInteractions(outbox);
    }

    @Test
    @DisplayName("An account no longer KYC PENDING throws, so the decision rolls back, and queues no event")
    void accountDisagrees() {
        applicant("ABCPM1234Q", LocalDate.of(1990, 5, 17));
        when(kyc.setAccountKycStatus(anyLong(), anyString())).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> decider.decide(CLIENT));

        verifyNoInteractions(outbox);
    }

    @Test
    @DisplayName("No account or profile for a pending verification throws before anything is written")
    void noApplicant() {
        assertThrows(IllegalStateException.class, () -> decider.decide(CLIENT));

        verify(kyc, never()).decide(anyLong(), any(), any(), any());
        verifyNoInteractions(provider, outbox);
    }
}
