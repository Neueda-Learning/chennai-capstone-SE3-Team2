package com.yellow.trade.mappers;

import com.yellow.enums.AccountStatus;

import java.math.BigDecimal;
import java.time.Instant;

public class AccountRow {

    private Long clientId;
    private String accountRef;
    private String holderName;
    private AccountStatus status;
    private BigDecimal balance;
    private BigDecimal blockedFunds;
    private int version;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }

    public String getAccountRef() { return accountRef; }
    public void setAccountRef(String accountRef) { this.accountRef = accountRef; }

    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }

    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public BigDecimal getBlockedFunds() { return blockedFunds; }
    public void setBlockedFunds(BigDecimal blockedFunds) { this.blockedFunds = blockedFunds; }

    /** The version the row was READ at. Every write names it and increments it. */
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    /** Maintained by a database trigger on every write. See migration 003. */
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    /** Derived, never stored: what rule 6 tests a buy against. */
    public BigDecimal availableFunds() {
        return balance.subtract(blockedFunds);
    }
}
