package com.yellow.exceptions;

public class AccountNotActiveException extends TradeException {

    public AccountNotActiveException() {
        super("ACC-403", "Account not active");
    }
}