package com.yellow.exceptions;

public class AccountNotFoundException extends TradeException {

    private final Long requestedAccountId;

    public AccountNotFoundException(Long requestedAccountId) {
        super("ACC-404", "Account not found");
        this.requestedAccountId = requestedAccountId;
    }

    public Long requestedAccountId() {
        return requestedAccountId;
    }

}
