package com.yellow.trade.payments;

import com.yellow.trade.mappers.BankAccountRow;
import com.yellow.trade.mappers.PaymentMapper;
import com.yellow.trade.mappers.TransferRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentDeciderTest {

    private static final long TRANSFER = 7L;
    private static final long CLIENT = 3L;

    private final PaymentMapper payments = mock(PaymentMapper.class);
    private final PaymentDecider decider = new PaymentDecider(payments, new StubPaymentGateway());

    @BeforeEach
    void aBankAccountAndRowsThatUpdate() {
        BankAccountRow bank = new BankAccountRow();
        bank.setAccountNumber("509876543210");
        bank.setIfsc("DEMO0000001");
        bank.setHolderName("Rohan Nair");
        when(payments.findBankAccount(CLIENT)).thenReturn(bank);
        when(payments.decide(anyLong(), anyString(), any(), anyString())).thenReturn(1);
        when(payments.credit(anyLong(), any())).thenReturn(1);
        when(payments.settleWithdrawal(anyLong(), any())).thenReturn(1);
        when(payments.releaseHold(anyLong(), any())).thenReturn(1);
    }

    private void pending(String direction, String amount) {
        TransferRow row = new TransferRow();
        row.setTransferId(TRANSFER);
        row.setClientId(CLIENT);
        row.setDirection(direction);
        row.setAmount(new BigDecimal(amount));
        row.setStatus("PENDING");
        when(payments.findById(TRANSFER)).thenReturn(row);
    }

    @Test
    @DisplayName("A deposit that succeeds is credited")
    void depositCredited() {
        pending("DEPOSIT", "5000.0000");

        assertThat(decider.decide(TRANSFER), is(Optional.of("SUCCESS")));

        verify(payments).decide(TRANSFER, "SUCCESS", null, "STUB-00000007");
        verify(payments).credit(CLIENT, new BigDecimal("5000.0000"));
    }

    @Test
    @DisplayName("A deposit that fails moves nothing, and keeps the reason")
    void depositDeclined() {
        pending("DEPOSIT", "250000.0000");

        assertThat(decider.decide(TRANSFER), is(Optional.of("FAILED")));

        verify(payments).decide(TRANSFER, "FAILED", StubPaymentGateway.OVER_LIMIT, "STUB-00000007");
        verify(payments, never()).credit(anyLong(), any());
    }

    @Test
    @DisplayName("A withdrawal that succeeds takes the held amount out of the account")
    void withdrawalSettled() {
        pending("WITHDRAWAL", "5000.0000");

        assertThat(decider.decide(TRANSFER), is(Optional.of("SUCCESS")));

        verify(payments).settleWithdrawal(CLIENT, new BigDecimal("5000.0000"));
        verify(payments, never()).releaseHold(anyLong(), any());
    }

    @Test
    @DisplayName("A withdrawal that fails releases its hold")
    void withdrawalReleased() {
        pending("WITHDRAWAL", "250000.0000");

        assertThat(decider.decide(TRANSFER), is(Optional.of("FAILED")));

        verify(payments).releaseHold(CLIENT, new BigDecimal("250000.0000"));
        verify(payments, never()).settleWithdrawal(anyLong(), any());
    }

    @Test
    @DisplayName("Decided by another run first: no money moves")
    void alreadyDecided() {
        pending("DEPOSIT", "5000.0000");
        when(payments.decide(anyLong(), anyString(), any(), anyString())).thenReturn(0);

        assertThat(decider.decide(TRANSFER), is(Optional.empty()));

        verify(payments, never()).credit(anyLong(), any());
    }

    @Test
    @DisplayName("No account to move money on throws, so the decision rolls back")
    void noAccountRow() {
        pending("DEPOSIT", "5000.0000");
        when(payments.credit(anyLong(), any())).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> decider.decide(TRANSFER));
    }

    @Test
    @DisplayName("A transfer that is no longer PENDING is left alone")
    void notPending() {
        pending("DEPOSIT", "5000.0000");
        when(payments.findById(TRANSFER)).thenAnswer(inv -> {
            TransferRow row = new TransferRow();
            row.setTransferId(TRANSFER);
            row.setClientId(CLIENT);
            row.setDirection("DEPOSIT");
            row.setAmount(new BigDecimal("5000"));
            row.setStatus("SUCCESS");
            return row;
        });

        assertThat(decider.decide(TRANSFER), is(Optional.empty()));

        verify(payments, never()).decide(anyLong(), anyString(), isNull(), anyString());
    }
}
