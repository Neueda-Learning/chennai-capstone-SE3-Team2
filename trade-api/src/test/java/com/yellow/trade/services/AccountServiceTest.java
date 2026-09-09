package com.yellow.trade.services;

import com.yellow.enums.AccountStatus;
import com.yellow.exceptions.AccountNotActiveException;
import com.yellow.exceptions.AccountNotFoundException;
import com.yellow.trade.dto.AccountResponse;
import com.yellow.trade.dto.BalanceResponse;
import com.yellow.trade.dto.OrderHistoryEntry;
import com.yellow.trade.dto.PositionResponse;
import com.yellow.trade.mappers.AccountMapper;
import com.yellow.trade.mappers.AccountRow;
import com.yellow.trade.mappers.OrderHistoryRow;
import com.yellow.trade.mappers.OrderMapper;
import com.yellow.trade.mappers.PositionMapper;
import com.yellow.trade.mappers.PositionRow;
import com.yellow.trade.security.TokenAccountContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private AccountMapper accountMapper;
    @Mock private PositionMapper positionMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private TokenAccountContext tokenAccountContext;

    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(accountMapper, positionMapper, orderMapper, tokenAccountContext);
        // most tests are about account 1 and a token that legitimately reaches it --
        // lenient() because the "unknown account" tests never get far enough to check this
        lenient().when(tokenAccountContext.currentAccountId()).thenReturn(1L);
    }

    private AccountRow accountRow() {
        AccountRow row = new AccountRow();
        row.id = 1L;
        row.accountId = "ACC-000001";
        row.holderName = "Priya Menon";
        row.cashBalance = new BigDecimal("24500.75");
        row.currency = "USD";
        row.status = AccountStatus.ACTIVE;
        row.version = 7;
        row.lastUpdated = Instant.now();
        return row;
    }

    @Test
    void getAccountReturnsResponseWhenRowExists() {
        when(accountMapper.findById(1L)).thenReturn(accountRow());

        AccountResponse response = service.getAccount(1L);

        assertThat(response.getId(), is(equalTo(1L)));
        assertThat(response.getAccountId(), is(equalTo("ACC-000001")));
        assertThat(response.getHolderName(), is(equalTo("Priya Menon")));
    }

    @Test
    void getAccountThrowsAccountNotFoundWhenRowIsNull() {
        when(accountMapper.findById(99L)).thenReturn(null);

        assertThrows(AccountNotFoundException.class, () -> service.getAccount(99L));
    }

    @Test
    void getAccountRefusesWhenTokenReachesADifferentAccount() {
        when(accountMapper.findById(2L)).thenReturn(accountRow());
        when(tokenAccountContext.currentAccountId()).thenReturn(1L); // token is for 1, request is for 2

        assertThrows(AccountNotActiveException.class, () -> service.getAccount(2L));
    }

    @Test
    void getBalanceMapsCashBalanceAndCurrency() {
        when(accountMapper.findById(1L)).thenReturn(accountRow());

        BalanceResponse response = service.getBalance(1L);

        assertThat(response.getCashBalance(), is(comparesEqualTo(new BigDecimal("24500.75"))));
        assertThat(response.getCurrency(), is(equalTo("USD")));
    }

    @Test
    void getPositionsFiltersOutZeroQuantityHoldings() {
        when(accountMapper.findById(1L)).thenReturn(accountRow());

        PositionRow held = new PositionRow();
        held.accountId = 1L;
        held.symbol = "ACME";
        held.quantity = new BigDecimal("100");
        held.averageCost = new BigDecimal("25.50");

        PositionRow closed = new PositionRow();
        closed.accountId = 1L;
        closed.symbol = "MSFT";
        closed.quantity = BigDecimal.ZERO;
        closed.averageCost = new BigDecimal("300.00");

        when(positionMapper.findByAccountId(1L)).thenReturn(List.of(held, closed));

        List<PositionResponse> positions = service.getPositions(1L);

        assertThat(positions, hasSize(1));
        assertThat(positions.get(0).getSymbol(), is(equalTo("ACME")));
        assertThat(positions.get(0).getQuantity(), is(equalTo(100)));
    }

    @Test
    void getPositionsThrowsAccountNotFoundEvenWhenPositionListWouldBeEmpty() {
        when(accountMapper.findById(99L)).thenReturn(null);

        assertThrows(AccountNotFoundException.class, () -> service.getPositions(99L));
    }

    @Test
    void getOrdersMapsRowsWithOrdPrefixOnOrderId() {
        when(accountMapper.findById(1L)).thenReturn(accountRow());

        UUID rawId = UUID.randomUUID();
        OrderHistoryRow row = new OrderHistoryRow();
        row.orderId = rawId;
        row.accountId = 1L;
        row.symbol = "ACME";
        row.quantity = new BigDecimal("100");
        row.price = new BigDecimal("25.50");
        row.createdOn = Instant.now();

        when(orderMapper.findByAccountId(1L, null, null, null)).thenReturn(List.of(row));

        List<OrderHistoryEntry> orders = service.getOrders(1L, null, null, null);

        assertThat(orders, hasSize(1));
        assertThat(orders.get(0).getOrderId(), is(equalTo("ORD-" + rawId)));
    }
}