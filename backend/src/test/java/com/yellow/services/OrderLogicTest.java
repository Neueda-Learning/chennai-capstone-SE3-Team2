package com.yellow.services;

import com.yellow.dto.PlaceOrderRequest;
import com.yellow.entities.Account;
import com.yellow.entities.Instrument;
import com.yellow.entities.Order;
import com.yellow.enums.OrderSide;
import com.yellow.enums.AccountStatus;
import com.yellow.enums.OrderStatus;
import com.yellow.enums.Reason;
import com.yellow.entities.Position;
import com.yellow.enums.AssetClass;
import com.yellow.exceptions.*;
import com.yellow.repositories.AccountRepository;
import com.yellow.repositories.InstrumentRepository;
import com.yellow.repositories.OrderRepository;
import com.yellow.repositories.PositionRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class OrderLogicTest {

    @Mock private AccountRepository accountRepo;
    @Mock private InstrumentRepository instrumentRepo;
    @Mock private PositionRepository positionRepo;
    @Mock private OrderRepository orderRepo;

    private OrderService orderService;
    private PlaceOrderRequest validBuyRequest;
    private PlaceOrderRequest validSellRequest;

    @BeforeAll
    static void beforeAll() {
        System.out.println("Initializing OrderLogicTest Suite...");
    }

    @BeforeEach
    void setUp() {
        orderService = new OrderService(accountRepo, instrumentRepo, positionRepo, orderRepo);
        validBuyRequest = new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, 10, new BigDecimal("100.00"), "key-1234");
        validSellRequest = new PlaceOrderRequest(1L, "AAPL", OrderSide.SELL, 10, new BigDecimal("100.00"), "key-1234");
    }

    private void stubValidAccount() {
        Account activeAccount = new Account(1L, "REF-1", 100L, new BigDecimal("5000.00"), BigDecimal.ZERO, AccountStatus.ACTIVE, 1);
        when(accountRepo.findById(1L)).thenReturn(Optional.of(activeAccount));
    }

    private void stubValidInstrument() {
        Instrument instrument = new Instrument(2L, "AAPL", "Apple Inc", AssetClass.EQUITY, "USD", true);
        when(instrumentRepo.findBySymbol("AAPL")).thenReturn(Optional.of(instrument));
    }

    @Test
    @DisplayName("Should successfully place a buy order when all rules pass")
    void shouldSuccessfullyPlaceBuyOrderWhenAllRulesPass() {
        stubValidAccount();
        stubValidInstrument();
        when(orderRepo.existsByAccountAndKey(1L, "key-1234")).thenReturn(false);
        when(orderRepo.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Order order = orderService.placeOrder(validBuyRequest);

        assertThat(order, is(notNullValue()));
        assertThat(order.status(), is(equalTo(OrderStatus.NEW)));
        verify(orderRepo).save(any(Order.class));
    }

    @Test
    @DisplayName("Should successfully place a sell order when holdings exist")
    void shouldSuccessfullyPlaceSellOrderWhenHoldingsExist() {
        stubValidAccount();
        stubValidInstrument();
        Position position = new Position(1L, 1L, 2L, new BigDecimal("50"), new BigDecimal("90.00"));
        when(positionRepo.find(1L, 2L)).thenReturn(Optional.of(position));
        when(orderRepo.existsByAccountAndKey(1L, "key-1234")).thenReturn(false);
        when(orderRepo.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Order order = orderService.placeOrder(validSellRequest);

        assertThat(order, is(notNullValue()));
        assertThat(order.status(), is(equalTo(OrderStatus.NEW)));
    }

    @Test
    @DisplayName("Should throw AccountNotFound carrying the requested id when account ID does not exist")
    void rule1_shouldThrowAccountNotFound_whenAccountIdDoesNotExist() {
        when(accountRepo.findById(1L)).thenReturn(Optional.empty());

        AccountNotFoundException ex = assertThrows(AccountNotFoundException.class,
                () -> orderService.placeOrder(validBuyRequest));

        assertThat(ex.catalogueCode(), is(equalTo("ACC-404")));
        assertThat(ex.requestedAccountId(), is(equalTo(1L)));
        verifyNoInteractions(instrumentRepo);
    }

    @Test
    @DisplayName("Should throw AccountNotActive carrying the actual status when account is suspended or closed")
    void rule2_shouldThrowAccountNotActive_whenAccountIsSuspendedOrClosed() {
        Account suspended = new Account(1L, "REF-1", 100L, new BigDecimal("5000.00"), BigDecimal.ZERO, AccountStatus.SUSPENDED, 1);
        when(accountRepo.findById(1L)).thenReturn(Optional.of(suspended));

        AccountNotActiveException ex = assertThrows(AccountNotActiveException.class,
                () -> orderService.placeOrder(validBuyRequest));

        assertThat(ex.catalogueCode(), is(equalTo("ACC-403")));
        assertThat(ex.actualStatus(), is(equalTo(AccountStatus.SUSPENDED)));
    }

    @Test
    @DisplayName("Should throw InstrumentNotFound with reason UNKNOWN when symbol is unknown")
    void rule3_shouldThrowInstrumentNotFound_whenSymbolIsUnknown() {
        stubValidAccount();
        when(instrumentRepo.findBySymbol("AAPL")).thenReturn(Optional.empty());

        InstrumentNotFoundException ex = assertThrows(InstrumentNotFoundException.class,
                () -> orderService.placeOrder(validBuyRequest));

        assertThat(ex.catalogueCode(), is(equalTo("INS-404")));
        assertThat(ex.requestedSymbol(), is(equalTo("AAPL")));
        assertThat(ex.reason(), is(equalTo(Reason.UNKNOWN)));
    }

    @Test
    @DisplayName("Should throw InstrumentNotFound with reason NOT_TRADABLE when instrument is delisted, " +
            "sharing the unknown-symbol case's code but not its reason")
    void rule3_shouldThrowInstrumentNotFound_whenInstrumentIsNotTradable() {
        stubValidAccount();
        Instrument delisted = new Instrument(2L, "AAPL", "Apple Inc", AssetClass.EQUITY, "USD", false);
        when(instrumentRepo.findBySymbol("AAPL")).thenReturn(Optional.of(delisted));

        InstrumentNotFoundException ex = assertThrows(InstrumentNotFoundException.class,
                () -> orderService.placeOrder(validBuyRequest));

        assertThat(ex.catalogueCode(), is(equalTo("INS-404")));
        assertThat(ex.reason(), is(equalTo(Reason.NOT_TRADABLE)));
        // one code, two distinct reasons -- the typed field is what tells the two cases apart,
        // not a second exception type.
        assertThat("the outward code must match the unknown-symbol case",
                ex.catalogueCode(), is(equalTo("INS-404")));
    }

    @Test
    @DisplayName("Should throw InvalidOrder naming the quantity field when quantity is invalid at trade level")
    void rule4_shouldThrowInvalidOrder_whenQuantityIsInvalidAtTradeLevel() {
        stubValidAccount();
        stubValidInstrument();
        PlaceOrderRequest badQty = new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, 0, new BigDecimal("100.00"), "key-1234");

        InvalidOrderException ex = assertThrows(InvalidOrderException.class,
                () -> orderService.placeOrder(badQty));

        assertThat(ex.catalogueCode(), is(equalTo("ORD-422")));
        assertThat(ex.field(), is(equalTo("quantity")));
        assertThat(ex.submittedValue(), is(equalTo("0")));
    }

    @Test
    @DisplayName("Should throw InvalidOrder naming the price field when price is invalid at trade level")
    void rule5_shouldThrowInvalidOrder_whenPriceIsInvalidAtTradeLevel() {
        stubValidAccount();
        stubValidInstrument();
        PlaceOrderRequest badPrice = new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, 10, BigDecimal.ZERO, "key-1234");

        InvalidOrderException ex = assertThrows(InvalidOrderException.class,
                () -> orderService.placeOrder(badPrice));

        assertThat(ex.catalogueCode(), is(equalTo("ORD-422")));
        assertThat(ex.field(), is(equalTo("price")));
    }

    @Test
    @DisplayName("Should throw InsufficientFunds carrying required and available, without leaking the balance in the message")
    void rule6_shouldThrowInsufficientFunds_whenBuyOrderExceedsAvailableBalance() {
        Account poorAccount = new Account(1L, "REF-1", 100L, new BigDecimal("50.00"), BigDecimal.ZERO, AccountStatus.ACTIVE, 1);
        when(accountRepo.findById(1L)).thenReturn(Optional.of(poorAccount));
        stubValidInstrument();

        InsufficientFundsException ex = assertThrows(InsufficientFundsException.class,
                () -> orderService.placeOrder(validBuyRequest));

        assertThat(ex.catalogueCode(), is(equalTo("ORD-400")));
        assertThat(ex.required(), is(comparesEqualTo(new BigDecimal("1000.00"))));
        assertThat(ex.available(), is(comparesEqualTo(new BigDecimal("50.00"))));
        // the balance is a typed field for the server log, not something the caller sees in the message
        assertThat(ex.getMessage(), not(containsString("50")));
    }

    @Test
    @DisplayName("Should throw InsufficientHoldings reporting zero held when there is no position at all")
    void rule7_shouldThrowInsufficientHoldings_whenNoPositionExists() {
        stubValidAccount();
        stubValidInstrument();
        when(positionRepo.find(1L, 2L)).thenReturn(Optional.empty());

        InsufficientHoldingsException ex = assertThrows(InsufficientHoldingsException.class,
                () -> orderService.placeOrder(validSellRequest));

        assertThat(ex.catalogueCode(), is(equalTo("ORD-409")));
        assertThat(ex.requested(), is(comparesEqualTo(new BigDecimal("10"))));
        assertThat(ex.held(), is(comparesEqualTo(BigDecimal.ZERO)));
    }

    @Test
    @DisplayName("Should throw InsufficientHoldings reporting the actual held quantity when a position exists but is too small")
    void rule7_shouldThrowInsufficientHoldings_whenPositionExistsButIsTooSmall() {
        stubValidAccount();
        stubValidInstrument();
        Position smallPosition = new Position(1L, 1L, 2L, new BigDecimal("4"), new BigDecimal("90.00"));
        when(positionRepo.find(1L, 2L)).thenReturn(Optional.of(smallPosition));

        InsufficientHoldingsException ex = assertThrows(InsufficientHoldingsException.class,
                () -> orderService.placeOrder(validSellRequest));

        assertThat(ex.requested(), is(comparesEqualTo(new BigDecimal("10"))));
        assertThat(ex.held(), is(comparesEqualTo(new BigDecimal("4"))));
    }

    @Test
    @DisplayName("Should throw DuplicateOrder carrying the key when the pre-check finds it already used")
    void rule8_shouldThrowDuplicateOrder_whenIdempotencyKeyHasAlreadyBeenUsed() {
        stubValidAccount();
        stubValidInstrument();
        when(orderRepo.existsByAccountAndKey(1L, "key-1234")).thenReturn(true);

        DuplicateOrderException ex = assertThrows(DuplicateOrderException.class,
                () -> orderService.placeOrder(validBuyRequest));

        assertThat(ex.catalogueCode(), is(equalTo("ORD-409")));
        assertThat(ex.idempotencyKey(), is(equalTo("key-1234")));
        assertThat("the pre-check reads no id, unlike a duplicate caught at the write itself",
                ex.existingOrderId(), is(nullValue()));
    }

    @Test
    @DisplayName("Should fail Rule 1 before Rule 3 when account not found and symbol is unknown")
    void precedence_shouldFailRule1BeforeRule3_whenAccountNotFoundAndSymbolIsUnknown() {
        when(accountRepo.findById(1L)).thenReturn(Optional.empty());

        AccountNotFoundException ex = assertThrows(AccountNotFoundException.class,
                () -> orderService.placeOrder(validBuyRequest));
        assertThat(ex.catalogueCode(), is(equalTo("ACC-404")));
        verifyNoInteractions(instrumentRepo);
    }

    @Test
    @DisplayName("Should fail Rule 2 before Rule 6 when account is suspended and has no cash")
    void precedence_shouldFailRule2BeforeRule6_whenAccountIsSuspendedAndHasNoCash() {
        Account suspendedPoorAccount = new Account(1L, "REF-1", 100L, BigDecimal.ZERO, BigDecimal.ZERO, AccountStatus.SUSPENDED, 1);
        when(accountRepo.findById(1L)).thenReturn(Optional.of(suspendedPoorAccount));

        AccountNotActiveException ex = assertThrows(AccountNotActiveException.class,
                () -> orderService.placeOrder(validBuyRequest));
        assertThat(ex.catalogueCode(), is(equalTo("ACC-403")));
        verifyNoInteractions(instrumentRepo);
    }
}