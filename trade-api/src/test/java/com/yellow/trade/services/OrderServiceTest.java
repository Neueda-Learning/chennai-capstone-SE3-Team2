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

/**
 * The service's own responsibilities: authorisation, the lock, the fill, and
 * the conditional cancel. The eight rules are the domain's and are tested there.
 */
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

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(domainOrderService, accountMapper, instrumentMapper,
                orderMapper, positionMapper, caller, Clock.fixed(NOW, ZoneOffset.UTC));

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
        when(orderMapper.fillIfNew(any(), any(), any())).thenReturn(1);
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
    @DisplayName("a buy debits the consideration, opens the holding and fills the order")
    // 213:1 - PLACE ORDER COMMITS
    void buyMovesCashAndPositionTogether() {
        Order placed = order(OrderSide.BUY);
        when(domainOrderService.placeOrder(any())).thenReturn(placed);
        when(positionMapper.findOne(ACCOUNT, INSTRUMENT, "DELIVERY")).thenReturn(null);

        OrderResponse response = service.placeOrder(request(OrderSide.BUY));

        // 10 units at 1450.00 is 14,500.00, debited at the version it read.
        verify(accountMapper).debitBalance(ACCOUNT, new BigDecimal("14500.0000"), 7);
        verify(positionMapper).insertPosition(eq(ACCOUNT), eq(INSTRUMENT), eq("DELIVERY"),
                eq(new BigDecimal("10.000000")), eq(new BigDecimal("1450.0000")));
        verify(orderMapper).fillIfNew(placed.orderId(), new BigDecimal("1450.00"), NOW);

        assertThat(response.status(), is(OrderStatus.FILLED));
        assertThat(response.orderId(), startsWith("ORD-"));
    }

    @Test
    @DisplayName("a buy into an existing holding recalculates the average cost")
    void buyRecalculatesAverageCost() {
        when(domainOrderService.placeOrder(any())).thenReturn(order(OrderSide.BUY));
        when(positionMapper.findOne(ACCOUNT, INSTRUMENT, "DELIVERY"))
                .thenReturn(positionRow("10.000000", "1350.0000"));

        service.placeOrder(request(OrderSide.BUY));

        // 10 at 1350 plus 10 at 1450 is 20 at 1400.
        verify(positionMapper).updatePosition(ACCOUNT, INSTRUMENT, "DELIVERY",
                new BigDecimal("20.000000"), new BigDecimal("1400.0000"));
    }

    @Test
    @DisplayName("a sell credits the proceeds and leaves the average cost alone")
    void sellCreditsAndKeepsAverageCost() {
        when(domainOrderService.placeOrder(any())).thenReturn(order(OrderSide.SELL));
        when(positionMapper.findOne(ACCOUNT, INSTRUMENT, "DELIVERY"))
                .thenReturn(positionRow("25.000000", "1350.0000"));

        service.placeOrder(request(OrderSide.SELL));

        verify(accountMapper).creditBalance(ACCOUNT, new BigDecimal("14500.0000"), 7);
        // Quantity falls; the average is untouched, which is what makes
        // realised profit and loss computable at the point of sale.
        verify(positionMapper).updatePosition(ACCOUNT, INSTRUMENT, "DELIVERY",
                new BigDecimal("15.000000"), new BigDecimal("1350.0000"));
    }

    @Test
    @DisplayName("selling the whole holding deletes the row rather than zeroing it")
    void sellingOutDeletesThePosition() {
        when(domainOrderService.placeOrder(any())).thenReturn(order(OrderSide.SELL));
        when(positionMapper.findOne(ACCOUNT, INSTRUMENT, "DELIVERY"))
                .thenReturn(positionRow("10.000000", "1350.0000"));

        service.placeOrder(request(OrderSide.SELL));

        verify(positionMapper).deletePosition(ACCOUNT, INSTRUMENT, "DELIVERY");
        verify(positionMapper, never()).updatePosition(anyLong(), anyLong(), anyString(), any(), any());
    }

    // ------------------------------------------------- the optimistic lock

    @Test
    @DisplayName("zero rows affected is refused, not treated as success")
    // 213:2 - FAILED ORDER, 213:3 - CONCURRENCY
    void lostRaceIsRefusedWithOrd409() {
        when(domainOrderService.placeOrder(any())).thenReturn(order(OrderSide.BUY));
        when(accountMapper.debitBalance(anyLong(), any(), anyInt())).thenReturn(0);

        StaleAccountVersionException e = assertThrows(
                StaleAccountVersionException.class, () -> service.placeOrder(request(OrderSide.BUY)));

        assertThat(e.catalogueCode(), is("ORD-409"));
        assertThat(e.expectedVersion(), is(7));
        // Nothing else moved: the transaction rolls back around all of it.
        verify(positionMapper, never()).insertPosition(anyLong(), anyLong(), anyString(), any(), any());
        verify(orderMapper, never()).fillIfNew(any(), any(), any());
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
