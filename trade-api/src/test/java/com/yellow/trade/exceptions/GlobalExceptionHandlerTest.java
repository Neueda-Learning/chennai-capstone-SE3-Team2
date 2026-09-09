package com.yellow.trade.exceptions;

import com.yellow.enums.AccountStatus;
import com.yellow.enums.OrderStatus;
import com.yellow.enums.Reason;
import com.yellow.exceptions.*;
import com.yellow.trade.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler();

    @Test
    void inactiveAccountMapsToAcc403() {

        AccountNotActiveException exception =
                new AccountNotActiveException(AccountStatus.SUSPENDED);

        var response = handler.handleAccountNotActive(exception);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("ACC-403", response.getBody().getErrorCode());
        assertEquals("Account not active", response.getBody().getMessage());
    }

    @Test
    void accountNotFoundMapsToAcc404() {

        AccountNotFoundException exception =
                new AccountNotFoundException(123L);

        var response = handler.handleAccountNotFound(exception);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("ACC-404", response.getBody().getErrorCode());
        assertEquals("Account not found", response.getBody().getMessage());
    }

    @Test
    void unknownInstrumentMapsToIns404() {

        InstrumentNotFoundException exception =
                new InstrumentNotFoundException("AAPL", Reason.UNKNOWN);

        var response = handler.handleInstrumentNotFound(exception);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("INS-404", response.getBody().getErrorCode());
        assertEquals("Instrument not found", response.getBody().getMessage());
    }

    @Test
    void insufficientFundsMapsToOrd400() {

        InsufficientFundsException exception =
                new InsufficientFundsException(
                        new BigDecimal("2000.00"),
                        new BigDecimal("1500.00"));

        var response = handler.handleInsufficientFunds(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("ORD-400", response.getBody().getErrorCode());
        assertEquals("Insufficient funds", response.getBody().getMessage());
    }

    @Test
    void insufficientHoldingsMapsToOrd409() {

        InsufficientHoldingsException exception =
                new InsufficientHoldingsException(
                        new BigDecimal("100"),
                        new BigDecimal("50"));

        var response = handler.handleInsufficientHoldings(exception);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("ORD-409", response.getBody().getErrorCode());
        assertEquals("Insufficient holdings", response.getBody().getMessage());
    }

    @Test
    void reusedIdempotencyKeyMapsToOrd409() {

        UUID existingOrderId = UUID.randomUUID();

        DuplicateOrderException exception =
                new DuplicateOrderException(
                        "test-idempotency-key",
                        existingOrderId);

        var response = handler.handleDuplicateOrder(exception);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("ORD-409", response.getBody().getErrorCode());
        assertEquals("Duplicate order", response.getBody().getMessage());
    }

    @Test
    void invalidInputMapsToOrd422() {

        InvalidOrderException exception =
                new InvalidOrderException("quantity", "-5");

        var response = handler.handleInvalidOrder(exception);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals("ORD-422", response.getBody().getErrorCode());
        assertEquals("Invalid Order", response.getBody().getMessage());
    }

    @ExceptionHandler(OrderNotCancellableException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotCancellable(
            OrderNotCancellableException ex) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        ex.catalogueCode(),
                        ex.getMessage()
                ));
    }

    @Test
    void orderNotCancellableMapsToOrd409() {

        OrderNotCancellableException exception =
                new OrderNotCancellableException(OrderStatus.FILLED);

        var response = handler.handleOrderNotCancellable(exception);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("ORD-409", response.getBody().getErrorCode());
        assertEquals("Order is not cancellable", response.getBody().getMessage());
    }

    @Test
    void orderNotFoundMapsToOrd409() {

        OrderNotFoundException exception =
                new OrderNotFoundException(UUID.randomUUID());

        var response = handler.handleOrderNotFound(exception);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("ORD-409", response.getBody().getErrorCode());
        assertEquals("Order not found", response.getBody().getMessage());
    }
}