package com.yellow;

import com.yellow.entities.Account;
import com.yellow.enums.AccountStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class AccountTest {

    @Test
    @DisplayName("Should credit amount and increase balance")
    void shouldCreditAmountAndIncreaseBalance() {
        Account account = new Account(1L, "REF-1", 100L, new BigDecimal("1000.00"), BigDecimal.ZERO, AccountStatus.ACTIVE, 1); // arrange
        account.credit(new BigDecimal("250.50")); // act
        assertEquals(new BigDecimal("1250.50"), account.availableFunds()); // assert
    }

    @Test
    @DisplayName("Should debit amount when funds are available")
    void shouldDebitAmountWhenFundsAreAvailable() {
        Account account = new Account(1L, "REF-1", 100L, new BigDecimal("1000.00"), BigDecimal.ZERO, AccountStatus.ACTIVE, 1); // arrange
        account.debit(new BigDecimal("300.00")); // act
        assertEquals(new BigDecimal("700.00"), account.availableFunds()); // assert
    }

    @Test
    @DisplayName("Should prevent negative balance and throw exception on overdraft")
    void shouldPreventNegativeBalanceAndThrowExceptionOnOverdraft() {
        Account account = new Account(1L, "REF-1", 100L, new BigDecimal("100.00"), BigDecimal.ZERO, AccountStatus.ACTIVE, 1); // arrange
        assertThrows(IllegalStateException.class, () -> account.debit(new BigDecimal("100.01"))); // act & assert
        assertEquals(new BigDecimal("100.00"), account.availableFunds(), "Balance should remain unchanged"); // assert
    }

    @Test
    @DisplayName("Should return false for canAfford when balance is less than required amount")
    void shouldReturnFalseForCanAffordWhenBalanceIsLessThanRequiredAmount() {
        Account account = new Account(1L, "REF-1", 100L, new BigDecimal("50.00"), BigDecimal.ZERO, AccountStatus.ACTIVE, 1); // arrange
        assertFalse(account.canAfford(new BigDecimal("50.01"))); // act & assert
        assertTrue(account.canAfford(new BigDecimal("50.00"))); // act & assert
    }

    @Test
    @DisplayName("Should maintain two decimal scale without floating point drift across multiple operations")
    void shouldMaintainTwoDecimalScaleWithoutFloatingPointDriftAcrossMultipleOps() {
        Account account = new Account(1L, "REF-1", 100L, new BigDecimal("0.00"), BigDecimal.ZERO, AccountStatus.ACTIVE, 1); // arrange
        BigDecimal creditAmount = new BigDecimal("0.10");
        BigDecimal debitAmount = new BigDecimal("0.03");
        
        for (int i = 0; i < 1000; i++) {
            account.credit(creditAmount); // act
            account.debit(debitAmount); // act
        }
        
        assertEquals(new BigDecimal("70.00"), account.availableFunds()); // assert
    }

    @Test
    @DisplayName("Should correctly identify active versus suspended or closed status")
    void shouldCorrectlyIdentifyActiveVersusSuspendedOrClosedStatus() {
        assertTrue(new Account(1L, "R1", 100L, BigDecimal.ZERO, BigDecimal.ZERO, AccountStatus.ACTIVE, 1).isActive());
        assertFalse(new Account(2L, "R2", 100L, BigDecimal.ZERO, BigDecimal.ZERO, AccountStatus.SUSPENDED, 1).isActive());
        assertFalse(new Account(3L, "R3", 100L, BigDecimal.ZERO, BigDecimal.ZERO, AccountStatus.CLOSED, 1).isActive());
    }
}
 