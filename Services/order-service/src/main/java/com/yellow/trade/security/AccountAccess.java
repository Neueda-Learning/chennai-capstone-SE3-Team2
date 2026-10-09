package com.yellow.trade.security;

import com.yellow.exceptions.AccountNotFoundException;
import com.yellow.trade.mappers.AccountMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Whether this caller may reach this account, for the Sprint 10 modules: each
 * of their routes calls it, on every request, before it reads anything. The
 * token filter has already verified the token; what it cannot decide is this.
 *
 * Another account is refused first, and logged, so a caller probing account
 * numbers learns nothing: a 403 whether or not the account exists. Only the
 * caller's own account can then be missing (404).
 */
@Component
public class AccountAccess {

    private static final Logger log = LoggerFactory.getLogger(AccountAccess.class);

    private final CallerAccount caller;
    private final AccountMapper accounts;

    public AccountAccess(CallerAccount caller, AccountMapper accounts) {
        this.caller = caller;
        this.accounts = accounts;
    }

    public void requireOwn(long accountId) {
        if (!caller.canReach(accountId)) {
            log.warn("ACC-403: token for account {} addressed account {}", caller.accountId(), accountId);
            throw new AccountNotReachableException();
        }
        if (accounts.findById(accountId) == null) {
            throw new AccountNotFoundException(accountId);
        }
    }
}
