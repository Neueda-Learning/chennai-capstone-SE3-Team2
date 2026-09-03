package com.yellow;

import com.yellow.entities.Account;
import com.yellow.enums.AccountStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountTest {

    @Test
    @DisplayName("Should credit amount and increase balance")
    void shouldCreditAmountAndIncreaseBalance() {
        Account account = new Account(1L, "REF-1", 100L, new BigDecimal("1000.00"), BigDecimal.ZERO, AccountStatus.ACTIVE, 1); // arrange
        account.credit(new BigDecimal("250.50")); // act
        assertThat(account.availableFunds(), is(equalTo(new BigDecimal("1250.50")))); // assert
    }

    @Test
    @DisplayName("Should debit amount when funds are available")
    void shouldDebitAmountWhenFundsAreAvailable() {
        Account account = new Account(1L, "REF-1", 100L, new BigDecimal("1000.00"), BigDecimal.ZERO, AccountStatus.ACTIVE, 1); // arrange
        account.debit(new BigDecimal("300.00")); // act
        assertThat(account.availableFunds(), is(equalTo(new BigDecimal("700.00")))); // assert
    }

    @Test
    @DisplayName("Should prevent negative balance and throw exception on overdraft")
    void shouldPreventNegativeBalanceAndThrowExceptionOnOverdraft() {
        Account account = new Account(1L, "REF-1", 100L, new BigDecimal("100.00"), BigDecimal.ZERO, AccountStatus.ACTIVE, 1); // arrange
        assertThrows(IllegalStateException.class, () -> account.debit(new BigDecimal("100.01"))); // act & assert
        assertThat("Balance should remain unchanged", account.availableFunds(), is(equalTo(new BigDecimal("100.00")))); // assert
    }

    @Test
    @DisplayName("Should return false for canAfford when balance is less than required amount")
    void shouldReturnFalseForCanAffordWhenBalanceIsLessThanRequiredAmount() {
        Account account = new Account(1L, "REF-1", 100L, new BigDecimal("50.00"), BigDecimal.ZERO, AccountStatus.ACTIVE, 1); // arrange
        assertThat(account.canAfford(new BigDecimal("50.01")), is(false)); // act & assert
        assertThat(account.canAfford(new BigDecimal("50.00")), is(true)); // act & assert
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

        assertThat(account.availableFunds(), is(equalTo(new BigDecimal("70.00")))); // assert
    }

    @Test
    @DisplayName("Should correctly identify active versus suspended or closed status")
    void shouldCorrectlyIdentifyActiveVersusSuspendedOrClosedStatus() {
        assertThat(new Account(1L, "R1", 100L, BigDecimal.ZERO, BigDecimal.ZERO, AccountStatus.ACTIVE, 1).isActive(), is(true));
        assertThat(new Account(2L, "R2", 100L, BigDecimal.ZERO, BigDecimal.ZERO, AccountStatus.SUSPENDED, 1).isActive(), is(false));
        assertThat(new Account(3L, "R3", 100L, BigDecimal.ZERO, BigDecimal.ZERO, AccountStatus.CLOSED, 1).isActive(), is(false));
    }
}