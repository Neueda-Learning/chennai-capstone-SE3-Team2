package com.yellow.trade.security;

import com.yellow.exceptions.TradeException;

/**
 * A valid token addressing an account that is not its own: 403 ACC-403, the
 * code and message contracts/portfolio-api.yaml gives it. Every Sprint 10
 * module route refuses this way, before reading anything about the account
 * addressed.
 */
public class AccountNotReachableException extends TradeException {

    public AccountNotReachableException() {
        super("ACC-403", "Account not accessible");
    }
}
