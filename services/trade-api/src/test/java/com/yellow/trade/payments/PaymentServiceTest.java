package com.yellow.trade.payments;

import com.yellow.enums.AccountStatus;
import com.yellow.enums.KycStatus;
import com.yellow.exceptions.AccountNotActiveException;
import com.yellow.exceptions.AccountNotFoundException;
import com.yellow.trade.mappers.AccountMapper;
import com.yellow.trade.mappers.AccountRow;
import com.yellow.trade.mappers.BankAccountRow;
import com.yellow.trade.mappers.PaymentMapper;
import com.yellow.trade.mappers.TransferRow;
import com.yellow.trade.security.CallerAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    private static final long ACCOUNT = 3L;
    private static final String KEY = "8f14e45f-ceea-467a-9575-ff1c2c1a3d5b";

    private final AccountMapper accounts = mock(AccountMapper.class);
    private final PaymentMapper payments = mock(PaymentMapper.class);
    private final CallerAccount caller = mock(CallerAccount.class);
    private final PaymentService service = new PaymentService(accounts, payments, caller);

    private AccountRow account;

    @BeforeEach
    void anActiveVerifiedAccountWithABank() {
        account = new AccountRow();
        account.setClientId(ACCOUNT);
        account.setStatus(AccountStatus.ACTIVE);
        account.setKycStatus(KycStatus.VERIFIED);
        when(accounts.findById(ACCOUNT)).thenReturn(account);
        when(caller.canReach(ACCOUNT)).thenReturn(true);
        BankAccountRow bank = new BankAccountRow();
        bank.setAccountNumber("509876543210");
        bank.setIfsc("DEMO0000001");
        bank.setHolderName("Rohan Nair");
        when(payments.findBankAccount(ACCOUNT)).thenReturn(bank);
        when(payments.insertPending(anyLong(), any(), anyString(), anyString())).thenReturn(7L);
        when(payments.findById(7L)).thenReturn(row("DEPOSIT", "5000.0000"));
    }

    private static TransferRow row(String direction, String amount) {
        TransferRow row = new TransferRow();
        row.setTransferId(7L);
        row.setClientId(ACCOUNT);
        row.setDirection(direction);
        row.setAmount(new BigDecimal(amount));
        row.setStatus("PENDING");
        row.setCreatedAt(Instant.parse("2026-10-03T10:00:00Z"));
        return row;
    }

    private static TransferRequest request(String amount) {
        return new TransferRequest(new BigDecimal(amount), KEY);
    }

    @Test
    @DisplayName("A deposit is recorded PENDING and returned, with nothing held")
    void depositRecordsPending() {
        TransferResponse response = service.deposit(ACCOUNT, request("5000"));

        assertThat(response.status(), is("PENDING"));
        assertThat(response.transferId(), is(7L));
        verify(payments).insertPending(ACCOUNT, new BigDecimal("5000"), "DEPOSIT", KEY);
        verify(payments, never()).hold(anyLong(), any());
    }

    @Test
    @DisplayName("A withdrawal holds the amount at once")
    void withdrawalHolds() {
        when(payments.findById(7L)).thenReturn(row("WITHDRAWAL", "5000.0000"));
        when(payments.hold(ACCOUNT, new BigDecimal("5000"))).thenReturn(1);

        assertThat(service.withdraw(ACCOUNT, request("5000")).direction(), is("WITHDRAWAL"));

        verify(payments).hold(ACCOUNT, new BigDecimal("5000"));
    }

    @Test
    @DisplayName("A withdrawal over the available cash is refused PAY-400")
    void withdrawalOverAvailable() {
        when(payments.hold(anyLong(), any())).thenReturn(0);

        PaymentRefusedException e = assertThrows(PaymentRefusedException.class,
                () -> service.withdraw(ACCOUNT, request("999999")));

        assertThat(e.code(), is("PAY-400"));
    }

    @Test
    @DisplayName("Another customer's account is ACC-403, and nothing is written")
    void notYourAccount() {
        when(caller.canReach(ACCOUNT)).thenReturn(false);

        assertThrows(AccountNotActiveException.class, () -> service.deposit(ACCOUNT, request("5000")));

        verify(payments, never()).insertPending(anyLong(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("An unknown account is ACC-404")
    void unknownAccount() {
        assertThrows(AccountNotFoundException.class, () -> service.deposit(99L, request("5000")));
    }

    @Test
    @DisplayName("No money moves for an account whose KYC has not passed, or that is not ACTIVE")
    void mustBeActiveAndVerified() {
        account.setKycStatus(KycStatus.PENDING);
        assertThrows(AccountNotActiveException.class, () -> service.deposit(ACCOUNT, request("5000")));

        account.setKycStatus(KycStatus.VERIFIED);
        account.setStatus(AccountStatus.SUSPENDED);
        assertThrows(AccountNotActiveException.class, () -> service.withdraw(ACCOUNT, request("5000")));

        verify(payments, never()).insertPending(anyLong(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("No bank account on file is PAY-404")
    void noBankAccount() {
        when(payments.findBankAccount(ACCOUNT)).thenReturn(null);

        PaymentRefusedException e = assertThrows(PaymentRefusedException.class,
                () -> service.deposit(ACCOUNT, request("5000")));

        assertThat(e.code(), is("PAY-404"));
    }

    @Test
    @DisplayName("The same transfer sent again returns the first one, and holds nothing twice")
    void replayReturnsTheFirst() {
        when(payments.insertPending(anyLong(), any(), anyString(), anyString())).thenReturn(null);
        TransferRow first = row("WITHDRAWAL", "5000.0000");
        first.setStatus("SUCCESS");
        when(payments.findByKey(ACCOUNT, KEY)).thenReturn(first);

        TransferResponse response = service.withdraw(ACCOUNT, request("5000.00"));

        assertThat(response.status(), is("SUCCESS"));
        verify(payments, never()).hold(anyLong(), any());
    }

    @Test
    @DisplayName("A different transfer under a used key is PAY-409")
    void keyReused() {
        when(payments.insertPending(anyLong(), any(), anyString(), anyString())).thenReturn(null);
        when(payments.findByKey(ACCOUNT, KEY)).thenReturn(row("DEPOSIT", "5000.0000"));

        PaymentRefusedException e = assertThrows(PaymentRefusedException.class,
                () -> service.deposit(ACCOUNT, request("6000")));

        assertThat(e.code(), is("PAY-409"));
    }

    @Test
    @DisplayName("The bank account comes back masked to its last four digits")
    void bankAccountMasked() {
        BankAccountResponse bank = service.bankAccount(ACCOUNT);

        assertThat(bank.accountNumberLast4(), is("3210"));
        assertThat(bank.ifsc(), is("DEMO0000001"));
    }
}
