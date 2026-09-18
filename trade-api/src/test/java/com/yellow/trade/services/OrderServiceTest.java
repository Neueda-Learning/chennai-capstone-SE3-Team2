package com.yellow.trade.services;

import com.yellow.dto.PlaceOrderRequest;
import com.yellow.entities.Order;
import com.yellow.enums.AccountStatus;
import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;
import com.yellow.exceptions.AccountNotActiveException;
import com.yellow.exceptions.AccountNotFoundException;
import com.yellow.exceptions.OrderNotCancellableException;
import com.yellow.exceptions.OrderNotFoundException;
import com.yellow.exceptions.StaleAccountVersionException;
import com.yellow.trade.dto.OrderResponse;
import com.yellow.trade.mappers.AccountMapper;
import com.yellow.trade.mappers.AccountRow;
import com.yellow.trade.mappers.InstrumentMapper;
import com.yellow.trade.mappers.InstrumentRow;
import com.yellow.trade.mappers.OrderMapper;
import com.yellow.trade.mappers.OrderRow;
import com.yellow.trade.mappers.PositionMapper;
import com.yellow.trade.mappers.PositionRow;
import com.yellow.trade.security.CallerAccount;
import org.springframework.context.ApplicationEventPublisher;
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
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-10T10:00:00Z");
    private static final Long ACCOUNT = 3L;
    private static final Long INSTRUMENT = 1L;

    @Mock private com.yellow.services.OrderService domainOrderService;
    @Mock private AccountMapper accountMapper;
    @Mock private InstrumentMapper instrumentMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private PositionMapper positionMapper;
    @Mock private CallerAccount caller;
    @Mock private ApplicationEventPublisher applicationEventPublisher;

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(domainOrderService, accountMapper, instrumentMapper,
                orderMapper, positionMapper, caller, Clock.fixed(NOW, ZoneOffset.UTC), applicationEventPublisher);

        when(caller.canReach(ACCOUNT)).thenReturn(true);
        when(caller.accountId()).thenReturn(ACCOUNT);
        when(accountMapper.findById(ACCOUNT)).thenReturn(accountRow(7));

        InstrumentRow instrument = new InstrumentRow();
        instrument.setInstrumentId(INSTRUMENT);
        instrument.setSymbol("ACME");
        when(instrumentMapper.findById(anyLong())).thenReturn(instrument);

        // the happy path everywhere unless a test says otherwise
        when(accountMapper.debitBalance(anyLong(), any(), anyInt())).thenReturn(1);
        when(accountMapper.creditBalance(anyLong(), any(), anyInt())).thenReturn(1);
        when(accountMapper.blockFunds(anyLong(), any(), anyInt())).thenReturn(1);
        when(accountMapper.releaseFunds(anyLong(), any(), anyInt())).thenReturn(1);
        when(orderMapper.insert(any())).thenReturn(1);
        when(positionMapper.insertPosition(anyLong(), anyLong(), anyString(), any(), any())).thenReturn(1);
        when(positionMapper.updatePosition(anyLong(), anyLong(), anyString(), any(), any())).thenReturn(1);
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
        row.setCreatedAt(NOW);
        row.setUpdatedAt(NOW);
        return row;
    }

    private static PlaceOrderRequest request(OrderSide side) {
        return new PlaceOrderRequest(ACCOUNT, "ACME", side, 10, new BigDecimal("1450.00"), "key-12345678");
    }

    private static Order order(OrderSide side) {
        return Order.place(ACCOUNT, INSTRUMENT, side, new BigDecimal("10"),
                new BigDecimal("1450.00"), "key-12345678");
    }

    private static PositionRow positionRow(String quantity, String averagePrice) {
        PositionRow row = new PositionRow();
        row.setPositionId(99L);
        row.setClientId(ACCOUNT);
        row.setInstrumentId(INSTRUMENT);
        row.setSymbol("ACME");
        row.setPositionType("DELIVERY");
        row.setQuantity(new BigDecimal(quantity));
        row.setAveragePrice(new BigDecimal(averagePrice));
        return row;
    }

    // ------------------------------------------------------ authorisation

    @Test
    @DisplayName("an unknown account is ACC-404 before the domain is consulted")
    void unknownAccountIsRefusedFirst() {
        when(accountMapper.findById(ACCOUNT)).thenReturn(null);

        assertThrows(AccountNotFoundException.class, () -> service.placeOrder(request(OrderSide.BUY)));
        verify(domainOrderService, never()).placeOrder(any());
    }

    @Test
    @DisplayName("a token that does not reach the account is ACC-403, and no rule runs")
    void tokenThatDoesNotReachIsRefused() {
        when(caller.canReach(ACCOUNT)).thenReturn(false);
        when(caller.accountId()).thenReturn(99L);

        assertThrows(AccountNotActiveException.class, () -> service.placeOrder(request(OrderSide.BUY)));
        verify(domainOrderService, never()).placeOrder(any());
    }

    // ------------------------------------------------------- the fill

    @Test
    @DisplayName("a buy order is placed at NEW without moving cash or position")
    // 213:1 - PLACE ORDER COMMITS (Sprint 7: no synchronous fill)
    // Story 611 will move cash and position during settlement, not at placement
    void buyIsPlacedWithoutMovingCashOrPosition() {
        Order placed = order(OrderSide.BUY);
        when(domainOrderService.placeOrder(any())).thenReturn(placed);

        OrderResponse response = service.placeOrder(request(OrderSide.BUY));

        // No cash movement at placement
        verify(accountMapper, never()).debitBalance(anyLong(), any(), anyInt());
        // No position creation at placement
        verify(positionMapper, never()).insertPosition(anyLong(), anyLong(), anyString(), any(), any());
        verify(positionMapper, never()).updatePosition(anyLong(), anyLong(), anyString(), any(), any());
        verify(positionMapper, never()).deletePosition(anyLong(), anyLong(), anyString());

        // But the notional IS reserved, at the LIMIT price and the version we
        // read. Not debited -- the money is still the customer's -- just no
        // longer available to the next order.
        verify(accountMapper).blockFunds(ACCOUNT, new BigDecimal("14500.0000"), 7);

        // Event published
        verify(applicationEventPublisher).publishEvent(any());

        assertThat(response.status(), is(OrderStatus.NEW));
        assertThat(response.orderId(), startsWith("ORD-"));
    }

    @Test
    @DisplayName("a buy into an existing holding leaves the position untouched at placement")
    void buyDoesNotModifyExistingPosition() {
        when(domainOrderService.placeOrder(any())).thenReturn(order(OrderSide.BUY));
        when(positionMapper.findOne(ACCOUNT, INSTRUMENT, "DELIVERY"))
                .thenReturn(positionRow("10.000000", "1350.0000"));

        service.placeOrder(request(OrderSide.BUY));

        // Position is NOT modified at placement
        verify(positionMapper, never()).updatePosition(anyLong(), anyLong(), anyString(), any(), any());
    }

    @Test
    @DisplayName("a sell order is placed at NEW without moving cash or position")
    void sellIsPlacedWithoutMovingCashOrPosition() {
        when(domainOrderService.placeOrder(any())).thenReturn(order(OrderSide.SELL));
        when(positionMapper.findOne(ACCOUNT, INSTRUMENT, "DELIVERY"))
                .thenReturn(positionRow("25.000000", "1350.0000"));

        service.placeOrder(request(OrderSide.SELL));

        // No cash movement at placement
        verify(accountMapper, never()).creditBalance(anyLong(), any(), anyInt());
        // No position modification at placement
        verify(positionMapper, never()).updatePosition(anyLong(), anyLong(), anyString(), any(), any());
        verify(positionMapper, never()).deletePosition(anyLong(), anyLong(), anyString());
    }


    // ------------------------------------------------- the optimistic lock
    // Placement writes to the account row again from Sprint 7 -- not the
    // balance, which only the executor moves, but blocked_funds. So the lock
    // is back on this path, guarding the reservation rather than the debit.

    @Test
    @DisplayName("a concurrent account update loses the reservation its race, and the order is refused")
    // 213:2 - FAILED ORDER, 213:3 - CONCURRENCY
    void placeOrderFailsWhenTheAccountMovedUnderIt() {
        when(domainOrderService.placeOrder(any())).thenReturn(order(OrderSide.BUY));
        // Somebody moved the account between the read that fed rule 6 and the
        // reservation: the version no longer matches and zero rows change.
        when(accountMapper.blockFunds(anyLong(), any(), anyInt())).thenReturn(0);

        // Refused rather than accepted, because affordability was decided
        // against a balance that no longer holds. ORD-409, and the customer
        // retries against fresh numbers.
        assertThrows(StaleAccountVersionException.class,
                () -> service.placeOrder(request(OrderSide.BUY)));

        // And nothing is announced for an order that did not survive its own
        // transaction.
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("a sell reserves nothing: it brings cash in rather than committing it")
    void sellReservesNothing() {
        when(domainOrderService.placeOrder(any())).thenReturn(order(OrderSide.SELL));

        service.placeOrder(request(OrderSide.SELL));

        // Reserving the HOLDING would be the equivalent mechanism for a sell,
        // and this schema has no column for it. Rule 7 is re-checked at
        // execution instead.
        verify(accountMapper, never()).blockFunds(anyLong(), any(), anyInt());
    }

    // ------------------------------------------------------------- cancel

    @Test
    @DisplayName("cancelling makes the transition conditional on the order still being NEW")
    void cancelIsConditional() {
        UUID orderId = UUID.randomUUID();
        when(orderMapper.findById(orderId)).thenReturn(orderRow(orderId, OrderStatus.NEW));
        when(orderMapper.cancelIfNew(eq(orderId), any())).thenReturn(1);

        OrderResponse response = service.cancelOrder(orderId);

        verify(orderMapper).cancelIfNew(orderId, NOW);
        assertThat(response.status(), is(OrderStatus.CANCELLED));
        assertThat(response.orderId(), is("ORD-" + orderId));
    }

    @Test
    @DisplayName("cancelling a buy gives the reservation back, exactly once")
    void cancelReleasesTheReservation() {
        UUID orderId = UUID.randomUUID();
        when(orderMapper.findById(orderId)).thenReturn(orderRow(orderId, OrderStatus.NEW));
        when(orderMapper.cancelIfNew(eq(orderId), any())).thenReturn(1);

        service.cancelOrder(orderId);

        // The same figure placement reserved. Without this the reservation is
        // stranded for ever: the account keeps cash it can never commit, and
        // slowly loses the ability to buy anything while appearing solvent.
        verify(accountMapper).releaseFunds(ACCOUNT, new BigDecimal("14500.0000"), 7);
    }

    @Test
    @DisplayName("a cancel that changed no row releases nothing: the guard decides who releases")
    void aLostCancelReleasesNothing() {
        UUID orderId = UUID.randomUUID();
        when(orderMapper.findById(orderId)).thenReturn(orderRow(orderId, OrderStatus.NEW));
        // Another delivery -- or another click -- moved the order off NEW first.
        when(orderMapper.cancelIfNew(eq(orderId), any())).thenReturn(0);

        assertThrows(OrderNotCancellableException.class, () -> service.cancelOrder(orderId));

        // This is what keeps the release exactly-once. Two concurrent cancels
        // both read NEW; only one moves the row, and only that one releases.
        // Releasing on both would drive blocked_funds negative.
        verify(accountMapper, never()).releaseFunds(anyLong(), any(), anyInt());
    }

    @Test
    @DisplayName("cancelling an order somebody else already resolved is ORD-409")
    // 213:4 - CANCELLED
    void cancelLosesTheRace() {
        UUID orderId = UUID.randomUUID();
        when(orderMapper.findById(orderId)).thenReturn(orderRow(orderId, OrderStatus.FILLED));
        when(orderMapper.cancelIfNew(eq(orderId), any())).thenReturn(0);

        OrderNotCancellableException e = assertThrows(
                OrderNotCancellableException.class, () -> service.cancelOrder(orderId));

        assertThat(e.catalogueCode(), is("ORD-409"));
    }

    @Test
    @DisplayName("an unknown order id raises the not-found case the contract pairs with 404")
    void unknownOrderIsNotFound() {
        UUID orderId = UUID.randomUUID();
        when(orderMapper.findById(orderId)).thenReturn(null);

        OrderNotFoundException e = assertThrows(
                OrderNotFoundException.class, () -> service.cancelOrder(orderId));

        // The catalogue has no ORD-404, so the code is ORD-409 while the
        // handler answers 404. That pairing is the contract's, not a slip.
        assertThat(e.catalogueCode(), is("ORD-409"));
        assertThat(e.getMessage(), is("Order not found"));
    }

    private static OrderRow orderRow(UUID orderId, OrderStatus status) {
        OrderRow row = new OrderRow();
        row.setOrderId(orderId);
        row.setClientId(ACCOUNT);
        row.setInstrumentId(INSTRUMENT);
        row.setSide(OrderSide.BUY);
        row.setQuantity(new BigDecimal("10.000000"));
        row.setPrice(new BigDecimal("1450.0000"));
        row.setStatus(status);
        row.setDatePlaced(NOW);
        return row;
    }
}
