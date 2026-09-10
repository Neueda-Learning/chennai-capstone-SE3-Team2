package com.yellow.trade.mappers;

import com.yellow.enums.AccountStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row of client_account, joined to the holder's name on client_profile.
 *
 * A row type rather than the domain's Account, because a row carries things
 * the domain has no opinion about -- the business reference a support call
 * quotes, when the account was opened -- and because the domain entity
 * validates its invariants in a constructor, which is the wrong behaviour
 * for something being reconstituted from storage.
 */
public class AccountRow {

    private Long clientId;
    private String accountRef;
    private String holderName;
    private AccountStatus status;
    private BigDecimal balance;
    private BigDecimal blockedFunds;
    private int version;
    private Instant createdAt;

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

    /** Derived, never stored: what rule 6 tests a buy against. */
    public BigDecimal availableFunds() {
        return balance.subtract(blockedFunds);
    }
}
