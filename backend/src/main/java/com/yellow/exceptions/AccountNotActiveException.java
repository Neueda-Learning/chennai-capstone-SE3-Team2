package com.yellow.exceptions;

import com.yellow.enums.AccountStatus;

public class AccountNotActiveException extends TradeException {

    private final AccountStatus actualStatus;

    public AccountNotActiveException(AccountStatus actualStatus) {
        super("ACC-403", "Account not active");
        this.actualStatus = actualStatus;
    }

    public AccountStatus actualStatus() {
        return actualStatus;
    }

}
