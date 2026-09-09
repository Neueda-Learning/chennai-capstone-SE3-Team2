package com.yellow.trade.dto;

import com.yellow.enums.AccountStatus;

import java.math.BigDecimal;
import java.time.Instant;

// contract: AccountResponse.accountId is the ONE place in the whole platform
// where "accountId" means the string business ref, not the numeric key.
public class AccountResponse {

    private final Long id;
    private final String accountId;
    private final String holderName;
    private final BigDecimal cashBalance;
    private final AccountStatus status;
    private final int version;
    private final Instant lastUpdated;

    public AccountResponse(Long id, String accountId, String holderName,
                            BigDecimal cashBalance, AccountStatus status,
                            int version, Instant lastUpdated) {
        this.id = id;
        this.accountId = accountId;
        this.holderName = holderName;
        this.cashBalance = cashBalance;
        this.status = status;
        this.version = version;
        this.lastUpdated = lastUpdated;
    }

    public Long getId() { return id; }
    public String getAccountId() { return accountId; }
    public String getHolderName() { return holderName; }
    public BigDecimal getCashBalance() { return cashBalance; }
    public AccountStatus getStatus() { return status; }
    public int getVersion() { return version; }
    public Instant getLastUpdated() { return lastUpdated; }
}