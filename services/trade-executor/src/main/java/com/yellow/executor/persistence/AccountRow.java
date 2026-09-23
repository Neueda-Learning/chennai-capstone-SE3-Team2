package com.yellow.executor.persistence;

import java.math.BigDecimal;

/** The account row, carrying the version the optimistic lock turns on. */
public class AccountRow {

    private Long clientId;
    private String accountRef;
    private String status;
    private BigDecimal balance;
    private BigDecimal blockedFunds;
    private int version;

    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }

    public String getAccountRef() { return accountRef; }
    public void setAccountRef(String accountRef) { this.accountRef = accountRef; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    /** Committed to somebody else's resting order. Not available to this one. */
    public BigDecimal getBlockedFunds() { return blockedFunds; }
    public void setBlockedFunds(BigDecimal blockedFunds) { this.blockedFunds = blockedFunds; }

    /** Read here, checked on write by story 611's optimistic lock. */
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
}
