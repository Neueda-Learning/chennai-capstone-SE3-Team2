package com.yellow.trade.security;

import com.yellow.exceptions.AccountNotFoundException;
import com.yellow.trade.mappers.AccountMapper;
import com.yellow.trade.mappers.AccountRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountAccessTest {

    private final CallerAccount caller = mock(CallerAccount.class);
    private final AccountMapper accounts = mock(AccountMapper.class);
    private final AccountAccess access = new AccountAccess(caller, accounts);

    @Test
    @DisplayName("the caller's own account passes")
    void own() {
        when(caller.canReach(3L)).thenReturn(true);
        when(accounts.findById(3L)).thenReturn(new AccountRow());

        access.requireOwn(3L);
    }

    @Test
    @DisplayName("another account is ACC-403 before anything about it is read, so a 404 cannot reveal which exist")
    void another() {
        when(caller.canReach(4L)).thenReturn(false);
        when(caller.accountId()).thenReturn(3L);

        AccountNotReachableException refused = assertThrows(AccountNotReachableException.class, () -> access.requireOwn(4L));

        assertThat(refused.catalogueCode(), is("ACC-403"));
        assertThat(refused.getMessage(), is("Account not accessible"));
        verify(accounts, never()).findById(4L);
    }

    @Test
    @DisplayName("the caller's own account gone is ACC-404")
    void gone() {
        when(caller.canReach(3L)).thenReturn(true);

        assertThrows(AccountNotFoundException.class, () -> access.requireOwn(3L));
    }
}
