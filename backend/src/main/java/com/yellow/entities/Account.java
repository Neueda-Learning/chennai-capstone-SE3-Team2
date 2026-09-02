package com.yellow.entities;

import com.yellow.enums.AccountStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public class Account {

    private static final int MONEY_SCALE = 2;

    private final Long accountId;

    private final String accountReference;

    private final Long clientId;

    private BigDecimal balance;
    private BigDecimal blockedFunds;
    private AccountStatus status;

    private final int loadedVersion;

    public Account(Long accountId,
                   String accountReference,
                   Long clientId,
                   BigDecimal balance,
                   BigDecimal blockedFunds,
                   AccountStatus status,
                   int loadedVersion) {

        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.accountReference =
                Objects.requireNonNull(accountReference, "accountReference");
        this.clientId = Objects.requireNonNull(clientId, "clientId");
        this.status = Objects.requireNonNull(status, "status");

        requireNotNegative(balance, "balance");
        requireNotNegative(blockedFunds, "blockedFunds");

        this.balance = money(balance);
        this.blockedFunds = money(blockedFunds);

        if (this.blockedFunds.compareTo(this.balance) > 0) {
            throw new IllegalArgumentException("blockedFunds " + this.blockedFunds
                    + " exceeds balance " + this.balance);
        }
        if (loadedVersion < 0) {
            throw new IllegalArgumentException("loadedVersion must not be negative");
        }
        this.loadedVersion = loadedVersion;
    }

    public BigDecimal availableFunds() {
        return balance.subtract(blockedFunds);
    }

    public boolean canAfford(BigDecimal amount) {
        requirePositive(amount, "amount");
        return availableFunds().compareTo(money(amount)) >= 0;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }
    public void block(BigDecimal amount) {
        requirePositive(amount, "amount");
        BigDecimal scaled = money(amount);

        if (availableFunds().compareTo(scaled) < 0) {
            throw new IllegalStateException("cannot block " + scaled
                    + ": available funds are " + availableFunds());
        }
        blockedFunds = blockedFunds.add(scaled);
    }

    public void release(BigDecimal amount) {
        requirePositive(amount, "amount");
        BigDecimal scaled = money(amount);

        if (blockedFunds.compareTo(scaled) < 0) {
            throw new IllegalStateException("cannot release " + scaled
                    + ": only " + blockedFunds + " is blocked");
        }
        blockedFunds = blockedFunds.subtract(scaled);
    }

    public void debit(BigDecimal amount) {
        requirePositive(amount, "amount");
        BigDecimal scaled = money(amount);

        if (balance.compareTo(scaled) < 0) {
            throw new IllegalStateException("debit of " + scaled
                    + " refused: balance is " + balance);
        }
        balance = balance.subtract(scaled);

        if (blockedFunds.compareTo(balance) > 0) {
            blockedFunds = balance;
        }
    }

    public void credit(BigDecimal amount) {
        requirePositive(amount, "amount");
        balance = balance.add(money(amount));
    }

    public void suspend() {
        status = AccountStatus.SUSPENDED;
    }

    public void reactivate() {
        if (status == AccountStatus.CLOSED) {
            throw new IllegalStateException("a closed account cannot be reactivated");
        }
        status = AccountStatus.ACTIVE;
    }

    public void close() {
        if (blockedFunds.signum() != 0) {
            throw new IllegalStateException(
                    "cannot close: " + blockedFunds + " is still blocked");
        }
        status = AccountStatus.CLOSED;
    }


    public Long accountId()             { return accountId; }
    public String accountReference()    { return accountReference; }
    public Long clientId()              { return clientId; }
    public BigDecimal balance()         { return balance; }
    public BigDecimal blockedFunds()    { return blockedFunds; }
    public AccountStatus status()       { return status; }
    public int loadedVersion()          { return loadedVersion; }


    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static void requirePositive(BigDecimal amount, String what) {
        if (amount == null) {
            throw new IllegalArgumentException(what + " must not be null");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    what + " must be positive, was " + amount);
        }
    }

    private static void requireNotNegative(BigDecimal amount, String what) {
        if (amount == null) {
            throw new IllegalArgumentException(what + " must not be null");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    what + " must not be negative, was " + amount);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Account that)) return false;
        return accountId.equals(that.accountId);
    }

    @Override
    public int hashCode() {
        return accountId.hashCode();
    }

    @Override
    public String toString() {
        return "Account{" + accountReference + ", balance=" + balance
                + ", blocked=" + blockedFunds + ", status=" + status + "}";
    }
}