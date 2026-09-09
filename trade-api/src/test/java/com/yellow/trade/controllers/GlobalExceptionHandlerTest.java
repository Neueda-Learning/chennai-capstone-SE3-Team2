package com.yellow.trade.controllers;

import com.yellow.enums.AccountStatus;
import com.yellow.enums.OrderStatus;
import com.yellow.enums.Reason;
import com.yellow.exceptions.AccountNotActiveException;
import com.yellow.exceptions.AccountNotFoundException;
import com.yellow.exceptions.DuplicateOrderException;
import com.yellow.exceptions.InstrumentNotFoundException;
import com.yellow.exceptions.InsufficientFundsException;
import com.yellow.exceptions.InsufficientHoldingsException;
import com.yellow.exceptions.InvalidOrderException;
import com.yellow.exceptions.OrderNotCancellableException;
import com.yellow.exceptions.OrderNotFoundException;
import com.yellow.trade.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void accountNotFoundMapsTo404WithAccCode() {
        ResponseEntity<ErrorResponse> response = handler.handle(new AccountNotFoundException(1L));

        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.NOT_FOUND)));
        assertThat(response.getBody().getErrorCode(), is(equalTo("ACC-404")));
    }

    @Test
    void accountNotActiveMapsTo403() {
        ResponseEntity<ErrorResponse> response = handler.handle(new AccountNotActiveException(AccountStatus.SUSPENDED));

        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.FORBIDDEN)));
        assertThat(response.getBody().getErrorCode(), is(equalTo("ACC-403")));
    }

    @Test
    void instrumentNotFoundMapsTo404WithInsCode() {
        ResponseEntity<ErrorResponse> response =
                handler.handle(new InstrumentNotFoundException("XYZ", Reason.UNKNOWN));

        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.NOT_FOUND)));
        assertThat(response.getBody().getErrorCode(), is(equalTo("INS-404")));
    }

    @Test
    void insufficientFundsMapsTo400WithOrdCode() {
        ResponseEntity<ErrorResponse> response = handler.handle(
                new InsufficientFundsException(new BigDecimal("1000.00"), new BigDecimal("50.00")));

        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.BAD_REQUEST)));
        assertThat(response.getBody().getErrorCode(), is(equalTo("ORD-400")));
    }

    @Test
    void insufficientHoldingsMapsTo409WithOrdCode() {
        ResponseEntity<ErrorResponse> response = handler.handle(
                new InsufficientHoldingsException(new BigDecimal("10"), new BigDecimal("4")));

        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.CONFLICT)));
        assertThat(response.getBody().getErrorCode(), is(equalTo("ORD-409")));
    }

    @Test
    void duplicateOrderMapsTo409WithOrdCode() {
        ResponseEntity<ErrorResponse> response =
                handler.handle(new DuplicateOrderException("key-1234", null));

        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.CONFLICT)));
        assertThat(response.getBody().getErrorCode(), is(equalTo("ORD-409")));
    }

    @Test
    void orderNotCancellableMapsTo409() {
        ResponseEntity<ErrorResponse> response =
                handler.handle(new OrderNotCancellableException(OrderStatus.FILLED));

        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.CONFLICT)));
        assertThat(response.getBody().getErrorCode(), is(equalTo("ORD-409")));
    }

    @Test
    void orderNotFoundMapsTo404StatusButOrdCode() {
        // the contract's own literal: 404 status, but ORD-409 as the code --
        // there is no ORD-404 in the catalogue
        ResponseEntity<ErrorResponse> response = handler.handle(new OrderNotFoundException(UUID.randomUUID()));

        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.NOT_FOUND)));
        assertThat(response.getBody().getErrorCode(), is(equalTo("ORD-409")));
    }

    @Test
    void invalidOrderOverridesDomainCodeToValCode() {
        // domain exception's own catalogueCode() is "ORD-422" -- the handler
        // must override it, since the contract has no ORD-422
        ResponseEntity<ErrorResponse> response = handler.handle(new InvalidOrderException("quantity", "0"));

        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.UNPROCESSABLE_ENTITY)));
        assertThat(response.getBody().getErrorCode(), is(equalTo("VAL-422")));
    }

    @Test
    void methodArgumentNotValidMapsTo422WithValCode() {
        // mocked rather than constructed for real -- MethodArgumentNotValidException
        // needs a real MethodParameter, and our handler doesn't inspect the
        // exception's contents anyway (message is hardcoded, not passed through)
        MethodArgumentNotValidException ex = org.mockito.Mockito.mock(MethodArgumentNotValidException.class);

        ResponseEntity<ErrorResponse> response = handler.handle(ex);

        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.UNPROCESSABLE_ENTITY)));
        assertThat(response.getBody().getErrorCode(), is(equalTo("VAL-422")));
    }

    @Test
    void bodyNeverContainsInternalDetail() {
        ResponseEntity<ErrorResponse> response = handler.handle(new InvalidOrderException("price", "-5.00"));

        assertThat("no field name or submitted value should leak into the message",
                response.getBody().getMessage(), not(containsString("-5.00")));
    }
}