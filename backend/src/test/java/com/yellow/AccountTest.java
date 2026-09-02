package com.yellow;

import com.yellow.entities.Account;
import com.yellow.enums.AccountStatus;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class AccountTest {

    @Test
    void shouldCreditAmountAndIncreaseBalance() {
        Account account = new Account(1L, "REF-1", 100L, new BigDecimal("1000.00"), BigDecimal.ZERO, AccountStatus.ACTIVE, 1);
        account.credit(new BigDecimal("250.50"));
        assertEquals(new BigDecimal("1250.50"), account.availableFunds());
    }

    @Test
    void shouldDebitAmountWhenFundsAreAvailable() {
        Account account = new Account(1L, "REF-1", 100L, new BigDecimal("1000.00"), BigDecimal.ZERO, AccountStatus.ACTIVE, 1);
        account.debit(new BigDecimal("300.00"));
        assertEquals(new BigDecimal("700.00"), account.availableFunds());
    }

    @Test
    void shouldPreventNegativeBalanceAndThrowExceptionOnOverdraft() {
        Account account = new Account(1L, "REF-1", 100L, new BigDecimal("100.00"), BigDecimal.ZERO, AccountStatus.ACTIVE, 1);
        assertThrows(IllegalStateException.class, () -> account.debit(new BigDecimal("100.01")));
        assertEquals(new BigDecimal("100.00"), account.availableFunds(), "Balance should remain unchanged");
    }

    @Test
    void shouldReturnFalseForCanAffordWhenBalanceIsLessThanRequiredAmount() {
        Account account = new Account(1L, "REF-1", 100L, new BigDecimal("50.00"), BigDecimal.ZERO, AccountStatus.ACTIVE, 1);
        assertFalse(account.canAfford(new BigDecimal("50.01")));
        assertTrue(account.canAfford(new BigDecimal("50.00")));
    }

    @Test
    void shouldMaintainTwoDecimalScaleWithoutFloatingPointDriftAcrossMultipleOps() {
        Account account = new Account(1L, "REF-1", 100L, new BigDecimal("0.00"), BigDecimal.ZERO, AccountStatus.ACTIVE, 1);
        BigDecimal creditAmount = new BigDecimal("0.10");
        BigDecimal debitAmount = new BigDecimal("0.03");
        
        for (int i = 0; i < 1000; i++) {
            account.credit(creditAmount);
            account.debit(debitAmount);
        }
        
        assertEquals(new BigDecimal("70.00"), account.availableFunds());
    }

    @Test
    void shouldCorrectlyIdentifyActiveVersusSuspendedOrClosedStatus() {
        assertTrue(new Account(1L, "R1", 100L, BigDecimal.ZERO, BigDecimal.ZERO, AccountStatus.ACTIVE, 1).isActive());
        assertFalse(new Account(2L, "R2", 100L, BigDecimal.ZERO, BigDecimal.ZERO, AccountStatus.SUSPENDED, 1).isActive());
        assertFalse(new Account(3L, "R3", 100L, BigDecimal.ZERO, BigDecimal.ZERO, AccountStatus.CLOSED, 1).isActive());
    }
}
 