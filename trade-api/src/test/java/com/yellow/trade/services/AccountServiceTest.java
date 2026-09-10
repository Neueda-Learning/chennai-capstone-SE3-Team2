package com.yellow.trade.services;

import com.yellow.enums.AccountStatus;
import com.yellow.exceptions.AccountNotActiveException;
import com.yellow.exceptions.AccountNotFoundException;
import com.yellow.trade.dto.BalanceResponse;
import com.yellow.trade.mappers.AccountMapper;
import com.yellow.trade.mappers.AccountRow;
import com.yellow.trade.mappers.OrderMapper;
import com.yellow.trade.mappers.PositionMapper;
import com.yellow.trade.security.CallerAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-10T10:00:00Z");

    @Mock private AccountMapper accountMapper;
    @Mock private PositionMapper positionMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private CallerAccount caller;

    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(accountMapper, positionMapper, orderMapper, caller,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(caller.accountId()).thenReturn(3L);
        when(caller.canReach(3L)).thenReturn(true);
        when(accountMapper.findById(3L)).thenReturn(row(AccountStatus.ACTIVE));
    }

    private static AccountRow row(AccountStatus status) {
        AccountRow row = new AccountRow();
        row.setClientId(3L);
        row.setAccountRef("ACC-000003");
        row.setHolderName("Rohan Nair");
        row.setStatus(status);
        row.setBalance(new BigDecimal("750000.0000"));
        row.setBlockedFunds(new BigDecimal("220000.0000"));
        row.setVersion(7);
        row.setCreatedAt(NOW);
        row.setUpdatedAt(NOW);
        return row;
    }

    @Test
    @DisplayName("accountId in the body is the string business reference, not the key")
    void accountIdIsTheBusinessReference() {
        assertThat(service.getAccount(3L).accountId(), is("ACC-000003"));
    }

    @Test
    @DisplayName("the balance is available cash: the total minus what pending orders hold")
    void balanceIsAvailableCash() {
        BalanceResponse balance = service.getBalance(3L);

        // 750,000 held, 220,000 committed. The contract says "available cash
        // only", and this is the figure rule 6 judges the next buy against.
        assertThat(balance.cashBalance(), comparesEqualTo(new BigDecimal("530000.0000")));
        assertThat(balance.currency(), is("INR"));
        assertThat(balance.asOf(), is(NOW));
    }

    @Test
    @DisplayName("the account body carries both identifiers, and they are not interchangeable")
    void bothIdentifiersArePresent() {
        var account = service.getAccount(3L);

        assertThat(account.id(), is(3L));                    // the numeric key
        assertThat(account.accountId(), is("ACC-000003"));   // the business reference
        assertThat(account.version(), is(7));
        assertThat(account.lastUpdated(), is(NOW));
    }

    @Test
    @DisplayName("an unknown account is ACC-404")
    void unknownAccountIsNotFound() {
        when(accountMapper.findById(999L)).thenReturn(null);

        AccountNotFoundException e = assertThrows(
                AccountNotFoundException.class, () -> service.getAccount(999L));

        assertThat(e.catalogueCode(), is("ACC-404"));
    }

    @Test
    @DisplayName("an account the token does not reach is ACC-403")
    void unreachableAccountIsForbidden() {
        when(accountMapper.findById(4L)).thenReturn(row(AccountStatus.ACTIVE));
        when(caller.canReach(4L)).thenReturn(false);

        AccountNotActiveException e = assertThrows(
                AccountNotActiveException.class, () -> service.getAccount(4L));

        // Identical to what a suspended account produces, so the difference
        // cannot be used to enumerate account keys.
        assertThat(e.catalogueCode(), is("ACC-403"));
        assertThat(e.getMessage(), is("Account not active"));
    }

    @Test
    @DisplayName("a SUSPENDED account can still be read: suspension stops trading, not looking")
    void suspendedAccountIsReadable() {
        when(accountMapper.findById(3L)).thenReturn(row(AccountStatus.SUSPENDED));

        assertThat(service.getAccount(3L).status(), is(AccountStatus.SUSPENDED));
    }

    @Test
    @DisplayName("an account holding nothing returns an empty list, having still been checked")
    void emptyHoldingsStillCheckTheAccount() {
        when(positionMapper.findByAccountId(3L)).thenReturn(List.of());

        assertThat(service.getPositions(3L), is(List.of()));
        // The check is not skipped just because the answer would be empty --
        // skipping it would confirm to a valid token which keys exist.
        verify(accountMapper).findById(3L);
    }

    @Test
    @DisplayName("an unknown account is refused before positions are read at all")
    void positionsAreNotReadForAnUnknownAccount() {
        when(accountMapper.findById(999L)).thenReturn(null);

        assertThrows(AccountNotFoundException.class, () -> service.getPositions(999L));
        verify(positionMapper, never()).findByAccountId(anyLong());
    }

    @Test
    @DisplayName("a null status filter is passed through as null, not as a literal")
    void nullStatusFilterStaysNull() {
        when(orderMapper.findByAccountId(anyLong(), any(), any(), any())).thenReturn(List.of());

        service.getOrders(3L, null, null, null);

        verify(orderMapper).findByAccountId(3L, null, null, null);
    }
}
