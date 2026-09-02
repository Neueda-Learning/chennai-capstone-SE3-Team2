package com.yellow.exceptions;

public class AccountNotFoundException extends TradeException {

    public AccountNotFoundException() {
        super("ACC-404", "Account not found");
    }
}