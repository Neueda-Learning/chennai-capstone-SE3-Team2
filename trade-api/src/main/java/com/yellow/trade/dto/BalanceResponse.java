package com.yellow.trade.dto;

import java.math.BigDecimal;
import java.time.Instant;

// contract: cash only, no holdings value
public class BalanceResponse {

    private final Long accountId;
    private final BigDecimal cashBalance;
    private final String currency;
    private final Instant asOf;

    public BalanceResponse(Long accountId, BigDecimal cashBalance, String currency, Instant asOf) {
        this.accountId = accountId;
        this.cashBalance = cashBalance;
        this.currency = currency;
        this.asOf = asOf;
    }

    public Long getAccountId() { return accountId; }
    public BigDecimal getCashBalance() { return cashBalance; }
    public String getCurrency() { return currency; }
    public Instant getAsOf() { return asOf; }
}