package com.yellow.exceptions;

import com.yellow.dto.PlaceOrderRequest;
import com.yellow.entities.Account;
import com.yellow.entities.Instrument;
import com.yellow.entities.Position;
import com.yellow.enums.AccountStatus;
import com.yellow.enums.AssetClass;
import com.yellow.enums.OrderSide;
import com.yellow.enums.Reason;
import com.yellow.exceptions.*;
import com.yellow.repositories.*;
import com.yellow.services.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionDetailTest {

    private InMemoryAccountRepository accounts;
    private InMemoryInstrumentRepository instruments;
    private InMemoryPositionRepository positions;
    private InMemoryOrderRepository orders;
    private OrderService service;

    @BeforeEach
    void setUp() {
        accounts = new InMemoryAccountRepository();
        instruments = new InMemoryInstrumentRepository();
        positions = new InMemoryPositionRepository();
        orders = new InMemoryOrderRepository();
        service = new OrderService(accounts, instruments, positions, orders);
    }

    private void givenAccount(AccountStatus status, String balance) {
        accounts.save(new Account(1L, "REF-1", 100L, new BigDecimal(balance),
                BigDecimal.ZERO, status, 1));
    }

    private void givenInstrument(boolean tradable) {
        instruments.save(new Instrument(2L, "AAPL", "Apple Inc",
                AssetClass.EQUITY, "USD", tradable));
    }

    private PlaceOrderRequest request(OrderSide side, int quantity, String price) {
        return new PlaceOrderRequest(1L, "AAPL", side, quantity,
                new BigDecimal(price), "key-1234");
    }

    @Test
    void accountNotFoundCarriesTheRequestedId() {
        AccountNotFoundException ex = assertThrows(AccountNotFoundException.class,
                () -> service.placeOrder(request(OrderSide.BUY, 10, "100.00")));

        assertEquals(1L, ex.requestedAccountId());
        assertEquals("Account not found", ex.getMessage());
    }

    @Test
    void accountNotActiveCarriesTheActualStatus() {
        givenAccount(AccountStatus.SUSPENDED, "5000.00");

        AccountNotActiveException ex = assertThrows(AccountNotActiveException.class,
                () -> service.placeOrder(request(OrderSide.BUY, 10, "100.00")));

        assertEquals(AccountStatus.SUSPENDED, ex.actualStatus());
        assertEquals("Account not active", ex.getMessage());
    }

    @Test
    void unknownSymbolAndDelistedSymbolShareACodeButNotAReason() {
        givenAccount(AccountStatus.ACTIVE, "5000.00");

        InstrumentNotFoundException unknown = assertThrows(InstrumentNotFoundException.class,
                () -> service.placeOrder(request(OrderSide.BUY, 10, "100.00")));
        assertEquals(Reason.UNKNOWN, unknown.reason());
        assertEquals("AAPL", unknown.requestedSymbol());

        givenInstrument(false);
        InstrumentNotFoundException delisted = assertThrows(InstrumentNotFoundException.class,
                () -> service.placeOrder(request(OrderSide.BUY, 10, "100.00")));
        assertEquals(Reason.NOT_TRADABLE, delisted.reason());

        // Same code and same message outward; only the typed field separates them.
        assertEquals(unknown.catalogueCode(), delisted.catalogueCode());
        assertEquals(unknown.getMessage(), delisted.getMessage());
    }

    @Test
    void insufficientFundsCarriesRequiredAndAvailable() {
        givenAccount(AccountStatus.ACTIVE, "50.00");
        givenInstrument(true);

        InsufficientFundsException ex = assertThrows(InsufficientFundsException.class,
                () -> service.placeOrder(request(OrderSide.BUY, 10, "100.00")));

        assertEquals(0, new BigDecimal("1000.00").compareTo(ex.required()));
        assertEquals(0, new BigDecimal("50.00").compareTo(ex.available()));

        // The balance must not leak into the body the customer sees.
        assertEquals("Insufficient funds", ex.getMessage());
        assertFalse(ex.getMessage().contains("50"));
    }

    @Test
    void insufficientHoldingsCarriesRequestedAndHeld() {
        givenAccount(AccountStatus.ACTIVE, "5000.00");
        givenInstrument(true);
        positions.save(new Position(1L, 1L, 2L,
                new BigDecimal("4"), new BigDecimal("90.00")));

        InsufficientHoldingsException ex = assertThrows(InsufficientHoldingsException.class,
                () -> service.placeOrder(request(OrderSide.SELL, 10, "100.00")));

        assertEquals(0, new BigDecimal("10").compareTo(ex.requested()));
        assertEquals(0, new BigDecimal("4").compareTo(ex.held()));
    }

    @Test
    void missingPositionReportsZeroHeld() {
        givenAccount(AccountStatus.ACTIVE, "5000.00");
        givenInstrument(true);

        InsufficientHoldingsException ex = assertThrows(InsufficientHoldingsException.class,
                () -> service.placeOrder(request(OrderSide.SELL, 10, "100.00")));

        assertEquals(0, BigDecimal.ZERO.compareTo(ex.held()));
    }

    @Test
    void invalidOrderNamesTheOffendingField() {
        givenAccount(AccountStatus.ACTIVE, "5000.00");
        givenInstrument(true);

        InvalidOrderException badQuantity = assertThrows(InvalidOrderException.class,
                () -> service.placeOrder(request(OrderSide.BUY, 0, "100.00")));
        assertEquals("quantity", badQuantity.field());
        assertEquals("0", badQuantity.submittedValue());

        InvalidOrderException badPrice = assertThrows(InvalidOrderException.class,
                () -> service.placeOrder(request(OrderSide.BUY, 10, "0.00")));
        assertEquals("price", badPrice.field());
        assertEquals("0.00", badPrice.submittedValue());
    }

    @Test
    void duplicateFromThePreCheckCarriesTheKeyButNoOrderId() {
        givenAccount(AccountStatus.ACTIVE, "5000.00");
        givenInstrument(true);
        service.placeOrder(request(OrderSide.BUY, 10, "100.00"));

        DuplicateOrderException ex = assertThrows(DuplicateOrderException.class,
                () -> service.placeOrder(request(OrderSide.BUY, 10, "100.00")));

        assertEquals("key-1234", ex.idempotencyKey());
        assertNull(ex.existingOrderId(), "the pre-check reads no id");
    }

    @Test
    void duplicateFromTheWriteCarriesTheWinningOrderId() {
        givenAccount(AccountStatus.ACTIVE, "5000.00");
        givenInstrument(true);

        // Bypass the advisory pre-check so the write itself refuses, as it does
        // when two concurrent callers both read false.
        OrderService racing = new OrderService(accounts, instruments, positions,
                new OrderRepository() {
                    @Override
                    public boolean existsByAccountAndKey(Long a, String k) {
                        return false;
                    }

                    @Override
                    public com.yellow.entities.Order save(com.yellow.entities.Order o) {
                        return orders.save(o);
                    }
                });

        racing.placeOrder(request(OrderSide.BUY, 10, "100.00"));

        DuplicateOrderException ex = assertThrows(DuplicateOrderException.class,
                () -> racing.placeOrder(request(OrderSide.BUY, 10, "100.00")));

        assertEquals("key-1234", ex.idempotencyKey());
        assertEquals(1L, ex.existingOrderId());
    }
}
