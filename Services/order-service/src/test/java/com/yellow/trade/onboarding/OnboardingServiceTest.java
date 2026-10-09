package com.yellow.trade.onboarding;

import com.yellow.trade.kyc.KycService;
import com.yellow.trade.mappers.OnboardingMapper;
import com.yellow.trade.onboarding.OnboardingService.DuplicateApplicationException;
import com.yellow.trade.onboarding.OnboardingService.InvalidApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OnboardingServiceTest {

    /** 21:00 UTC on 1 Oct is 02:30 IST on 2 Oct. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T21:00:00Z"), ZoneOffset.UTC);

    private final OnboardingMapper mapper = mock(OnboardingMapper.class);
    private final KycService kyc = mock(KycService.class);
    private final OnboardingService service = new OnboardingService(mapper, kyc, CLOCK);

    private static ApplicationRequest request(String dob) {
        return new ApplicationRequest("Priya Menon", dob, "priya@example.com",
                "+919812345611", "ABCPM1234Q", "12 Anna Nagar, Chennai", "509876543210", "DEMO0000001");
    }

    @BeforeEach
    void freshCustomerIs11() {
        when(mapper.nextClientId()).thenReturn(11L);
        when(mapper.insertAccount(anyLong(), anyString(), anyString(), anyString())).thenReturn(1);
        when(mapper.insertProfile(anyLong(), anyString(), any(), anyString(), anyString(), any())).thenReturn(1);
    }

    @Test
    @DisplayName("Creates the account, the profile, then the bank account in the applicant's name, then queues KYC")
    void createsCustomerAndQueuesKyc() {
        long clientId = service.apply(request("1990-05-17"));

        assertThat(clientId, is(11L));
        InOrder order = inOrder(mapper, kyc);
        order.verify(mapper).insertAccount(11L, "ACC-000011", "ABCPM1234Q", "IN30001000000011");
        order.verify(mapper).insertProfile(11L, "Priya Menon", LocalDate.of(1990, 5, 17),
                "priya@example.com", "+919812345611", "12 Anna Nagar, Chennai");
        order.verify(mapper).insertBankAccount(11L, "509876543210", "DEMO0000001", "Priya Menon");
        order.verify(kyc).submit(11L);
    }

    @Test
    @DisplayName("A registered PAN creates nothing and queues no KYC")
    void duplicatePan() {
        when(mapper.insertAccount(anyLong(), anyString(), anyString(), anyString())).thenReturn(0);

        assertThrows(DuplicateApplicationException.class, () -> service.apply(request("1990-05-17")));

        verify(mapper, never()).insertProfile(anyLong(), anyString(), any(), anyString(), anyString(), any());
        verify(mapper, never()).insertBankAccount(anyLong(), anyString(), anyString(), anyString());
        verifyNoInteractions(kyc);
    }

    @Test
    @DisplayName("A registered email throws, so the account already inserted is rolled back, and queues no KYC")
    void duplicateEmail() {
        when(mapper.insertProfile(anyLong(), anyString(), any(), anyString(), anyString(), any())).thenReturn(0);

        assertThrows(DuplicateApplicationException.class, () -> service.apply(request("1990-05-17")));

        verify(mapper, never()).insertBankAccount(anyLong(), anyString(), anyString(), anyString());
        verifyNoInteractions(kyc);
    }

    @Test
    @DisplayName("A date that does not exist is refused before anything is written")
    void impossibleDate() {
        InvalidApplicationException e = assertThrows(InvalidApplicationException.class,
                () -> service.apply(request("1990-02-30")));

        assertThat(e.getMessage(), is("dob is not a real date"));
        verify(mapper, never()).nextClientId();
    }

    @Test
    @DisplayName("A future date of birth is refused, judged on the Indian date")
    void futureDate() {
        // 2 Oct is today in India even though it is still 1 Oct in UTC: allowed.
        service.apply(request("2026-10-02"));

        InvalidApplicationException e = assertThrows(InvalidApplicationException.class,
                () -> service.apply(request("2026-10-03")));
        assertThat(e.getMessage(), is("dob is in the future"));
    }
}
