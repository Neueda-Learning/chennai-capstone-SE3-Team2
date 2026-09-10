package com.yellow.trade.services;

import com.yellow.dto.PlaceOrderRequest;
import com.yellow.entities.Order;
import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;
import com.yellow.exceptions.AccountNotActiveException;
import com.yellow.exceptions.AccountNotFoundException;
import com.yellow.exceptions.OrderNotCancellableException;
import com.yellow.exceptions.StaleAccountVersionException;
import com.yellow.trade.dto.OrderResponse;
import com.yellow.trade.mappers.AccountMapper;
import com.yellow.trade.mappers.AccountRow;
import com.yellow.trade.mappers.InstrumentMapper;
import com.yellow.trade.mappers.InstrumentRow;
import com.yellow.trade.mappers.OrderMapper;
import com.yellow.trade.mappers.OrderRow;
import com.yellow.trade.security.CallerAccount;
import com.yellow.enums.AccountStatus;
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
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The service's own responsibilities: authorisation, the lock, and the
 * conditional cancel. The rules themselves are the domain's and are tested
 * there.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-10T10:00:00Z");
    private static final Long ACCOUNT = 3L;

    @Mock private com.yellow.services.OrderService domainOrderService;
    @Mock private AccountMapper accountMapper;
    @Mock private InstrumentMapper instrumentMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private CallerAccount caller;

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(domainOrderService, accountMapper, instrumentMapper,
                orderMapper, caller, Clock.fixed(NOW, ZoneOffset.UTC));
        when(caller.canReach(ACCOUNT)).thenReturn(true);
        when(caller.accountId()).thenReturn(ACCOUNT);
        when(accountMapper.findById(ACCOUNT)).thenReturn(accountRow(7));

        InstrumentRow instrument = new InstrumentRow();
        instrument.setInstrumentId(1L);
        instrument.setSymbol("APEX");
        when(instrumentMapper.findById(anyLong())).thenReturn(instrument);
    }

    private static AccountRow accountRow(int version) {
        AccountRow row = new AccountRow();
        row.setClientId(ACCOUNT);
        row.setAccountRef("ACC-000003");
        row.setHolderName("Rohan Nair");
        row.setStatus(AccountStatus.ACTIVE);
        row.setBalance(new BigDecimal("750000.0000"));
        row.setBlockedFunds(new BigDecimal("220000.0000"));
        row.setVersion(version);
        return row;
    }

    private static PlaceOrderRequest request() {
        return new PlaceOrderRequest(ACCOUNT, "APEX", OrderSide.BUY, 10,
                new BigDecimal("1450.00"), "key-12345678");
    }

    private static Order placedOrder() {
        return Order.place(ACCOUNT, 1L, OrderSide.BUY, new BigDecimal("10"),
                new BigDecimal("1450.00"), "key-12345678");
    }

    // ------------------------------------------------------ authorisation

    @Test
    @DisplayName("an unknown account is ACC-404 before the domain is consulted")
    void unknownAccountIsRefusedFirst() {
        when(accountMapper.findById(ACCOUNT)).thenReturn(null);

        assertThrows(AccountNotFoundException.class, () -> service.placeOrder(request()));
        verify(domainOrderService, never()).placeOrder(any());
    }

    @Test
    @DisplayName("a token that does not reach the account is ACC-403, and no rule runs")
    void tokenThatDoesNotReachIsRefused() {
        when(caller.canReach(ACCOUNT)).thenReturn(false);
        when(caller.accountId()).thenReturn(99L);

        assertThrows(AccountNotActiveException.class, () -> service.placeOrder(request()));
        verify(domainOrderService, never()).placeOrder(any());
    }

    // ------------------------------------------------- the optimistic lock

    @Test
    @DisplayName("a buy blocks its notional against the version the row was read at")
    void buyBlocksNotionalUnderTheVersionItRead() {
        when(domainOrderService.placeOrder(any())).thenReturn(placedOrder());
        when(accountMapper.blockFunds(anyLong(), any(), anyInt())).thenReturn(1);

        OrderResponse response = service.placeOrder(request());

        // 10 units at 1450.00 is 14,500.00, blocked at version 7.
        verify(accountMapper).blockFunds(ACCOUNT, new BigDecimal("14500.00"), 7);
        assertThat(response.status(), is(OrderStatus.NEW));
    }

    @Test
    @DisplayName("zero rows affected is refused, not treated as success")
    void lostRaceIsRefusedWithOrd409() {
        when(domainOrderService.placeOrder(any())).thenReturn(placedOrder());
        when(accountMapper.blockFunds(anyLong(), any(), anyInt())).thenReturn(0);

        StaleAccountVersionException e = assertThrows(
                StaleAccountVersionException.class, () -> service.placeOrder(request()));

        assertThat(e.catalogueCode(), is("ORD-409"));
        assertThat(e.expectedVersion(), is(7));
    }

    @Test
    @DisplayName("a sell commits no cash")
    void sellDoesNotBlockFunds() {
        Order sell = Order.place(ACCOUNT, 1L, OrderSide.SELL, new BigDecimal("10"),
                new BigDecimal("1450.00"), "key-12345678");
        when(domainOrderService.placeOrder(any())).thenReturn(sell);

        service.placeOrder(new PlaceOrderRequest(ACCOUNT, "APEX", OrderSide.SELL, 10,
                new BigDecimal("1450.00"), "key-12345678"));

        verify(accountMapper, never()).blockFunds(anyLong(), any(), anyInt());
    }

    // ------------------------------------------------------------- cancel

    @Test
    @DisplayName("cancelling makes the transition conditional and releases the cash")
    void cancelIsConditionalAndReleasesCash() {
        UUID orderId = UUID.randomUUID();
        when(orderMapper.findById(orderId)).thenReturn(orderRow(orderId, OrderStatus.NEW));
        when(orderMapper.cancelIfNew(eq(orderId), any())).thenReturn(1);
        when(accountMapper.releaseFunds(anyLong(), any(), anyInt())).thenReturn(1);

        OrderResponse response = service.cancelOrder(orderId);

        verify(orderMapper).cancelIfNew(orderId, NOW);
        verify(accountMapper).releaseFunds(ACCOUNT, new BigDecimal("14500.0000"), 7);
        assertThat(response.status(), is(OrderStatus.CANCELLED));
    }

    @Test
    @DisplayName("cancelling an order somebody else already resolved is ORD-409")
    void cancelLosesTheRace() {
        UUID orderId = UUID.randomUUID();
        when(orderMapper.findById(orderId)).thenReturn(orderRow(orderId, OrderStatus.FILLED));
        when(orderMapper.cancelIfNew(eq(orderId), any())).thenReturn(0);

        OrderNotCancellableException e = assertThrows(
                OrderNotCancellableException.class, () -> service.cancelOrder(orderId));

        assertThat(e.catalogueCode(), is("ORD-409"));
        verify(accountMapper, never()).releaseFunds(anyLong(), any(), anyInt());
    }

    @Test
    @DisplayName("an unknown order id answers exactly as an uncancellable one does")
    void unknownOrderIsIndistinguishable() {
        UUID orderId = UUID.randomUUID();
        when(orderMapper.findById(orderId)).thenReturn(null);

        OrderNotCancellableException e = assertThrows(
                OrderNotCancellableException.class, () -> service.cancelOrder(orderId));

        // Same code and same message a FILLED order gets, so a valid token
        // cannot be used to discover which order ids exist.
        assertThat(e.catalogueCode(), is("ORD-409"));
        assertThat(e.getMessage(), is("Order is not cancellable"));
    }

    private static OrderRow orderRow(UUID orderId, OrderStatus status) {
        OrderRow row = new OrderRow();
        row.setOrderId(orderId);
        row.setClientId(ACCOUNT);
        row.setInstrumentId(1L);
        row.setSide(OrderSide.BUY);
        row.setQuantity(new BigDecimal("10.000000"));
        row.setPrice(new BigDecimal("1450.0000"));
        row.setStatus(status);
        row.setDatePlaced(NOW);
        return row;
    }
}
