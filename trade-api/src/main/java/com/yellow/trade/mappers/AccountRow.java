package com.yellow.trade.mappers;

import com.yellow.enums.AccountStatus;

import java.math.BigDecimal;
import java.time.Instant;

public class AccountRow {
    public Long id;
    public String accountId;
    public String holderName;
    public BigDecimal cashBalance;
    public String currency;
    public AccountStatus status;
    public int version;
    public Instant lastUpdated;
}