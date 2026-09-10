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
import com.yellow.exceptions.StaleAccountVersionException;
import com.yellow.exceptions.TradeException;
import com.yellow.trade.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // -------------------------------------------------------------------------
    // 404
    // -------------------------------------------------------------------------

    @Test
    void accountNotFoundMapsTo404WithAccCode() {
        ResponseEntity<ErrorResponse> response =
                handler.handle(new AccountNotFoundException(1L));

        assertThat(response.getStatusCode(), is(HttpStatus.NOT_FOUND));
        assertThat(response.getBody(), is(notNullValue()));
        assertThat(response.getBody().errorCode(), is("ACC-404"));
    }

    @Test
    void instrumentNotFoundMapsTo404WithInsCode() {
        ResponseEntity<ErrorResponse> response =
                handler.handle(
                        new InstrumentNotFoundException("XYZ", Reason.UNKNOWN)
                );

        assertThat(response.getStatusCode(), is(HttpStatus.NOT_FOUND));
        assertThat(response.getBody(), is(notNullValue()));
        assertThat(response.getBody().errorCode(), is("INS-404"));
    }

    @Test
    void orderNotFoundMapsTo404WithOrdCode() {
        ResponseEntity<ErrorResponse> response =
                handler.handle(
                        new OrderNotFoundException(UUID.randomUUID())
                );

        assertThat(response.getStatusCode(), is(HttpStatus.NOT_FOUND));
        assertThat(response.getBody(), is(notNullValue()));
        assertThat(response.getBody().errorCode(), is("ORD-409"));
    }

    // -------------------------------------------------------------------------
    // 403
    // -------------------------------------------------------------------------

    @Test
    void accountNotActiveMapsTo403() {
        ResponseEntity<ErrorResponse> response =
                handler.handle(
                        new AccountNotActiveException(AccountStatus.SUSPENDED)
                );

        assertThat(response.getStatusCode(), is(HttpStatus.FORBIDDEN));
        assertThat(response.getBody(), is(notNullValue()));
        assertThat(response.getBody().errorCode(), is("ACC-403"));
    }

    // -------------------------------------------------------------------------
    // 400
    // -------------------------------------------------------------------------

    @Test
    void insufficientFundsMapsTo400WithOrdCode() {
        ResponseEntity<ErrorResponse> response =
                handler.handle(
                        new InsufficientFundsException(
                                new BigDecimal("1000.00"),
                                new BigDecimal("50.00")
                        )
                );

        assertThat(response.getStatusCode(), is(HttpStatus.BAD_REQUEST));
        assertThat(response.getBody(), is(notNullValue()));
        assertThat(response.getBody().errorCode(), is("ORD-400"));
    }

    // -------------------------------------------------------------------------
    // 409
    // -------------------------------------------------------------------------

    @Test
    void insufficientHoldingsMapsTo409WithOrdCode() {
        ResponseEntity<ErrorResponse> response =
                handler.handle(
                        new InsufficientHoldingsException(
                                new BigDecimal("10"),
                                new BigDecimal("4")
                        )
                );

        assertThat(response.getStatusCode(), is(HttpStatus.CONFLICT));
        assertThat(response.getBody(), is(notNullValue()));
        assertThat(response.getBody().errorCode(), is("ORD-409"));
    }

    @Test
    void duplicateOrderMapsTo409WithOrdCode() {
        ResponseEntity<ErrorResponse> response =
                handler.handle(
                        new DuplicateOrderException("key-1234", null)
                );

        assertThat(response.getStatusCode(), is(HttpStatus.CONFLICT));
        assertThat(response.getBody(), is(notNullValue()));
        assertThat(response.getBody().errorCode(), is("ORD-409"));
    }

    @Test
    void orderNotCancellableMapsTo409() {
        ResponseEntity<ErrorResponse> response =
                handler.handle(
                        new OrderNotCancellableException(OrderStatus.FILLED)
                );

        assertThat(response.getStatusCode(), is(HttpStatus.CONFLICT));
        assertThat(response.getBody(), is(notNullValue()));
        assertThat(response.getBody().errorCode(), is("ORD-409"));
    }

    @Test
    void staleAccountVersionMapsTo409() {
        StaleAccountVersionException exception =
                mock(StaleAccountVersionException.class);

        when(exception.accountId()).thenReturn(1L);
        when(exception.expectedVersion()).thenReturn(1);

        ResponseEntity<ErrorResponse> response =
                handler.handle(exception);

        assertThat(response.getStatusCode(), is(HttpStatus.CONFLICT));
        assertThat(response.getBody(), is(notNullValue()));
    }

    // -------------------------------------------------------------------------
    // 422
    // -------------------------------------------------------------------------

    @Test
    void invalidOrderMapsTo422() {
        ResponseEntity<ErrorResponse> response =
                handler.handle(
                        new InvalidOrderException("quantity", "0")
                );

        assertThat(
                response.getStatusCode(),
                is(HttpStatus.UNPROCESSABLE_ENTITY)
        );
        assertThat(response.getBody(), is(notNullValue()));
    }

    @Test
    void methodArgumentNotValidMapsTo422WithValCode() {
        MethodArgumentNotValidException exception =
                mock(MethodArgumentNotValidException.class);

        when(exception.getFieldErrors())
                .thenReturn(Collections.emptyList());

        ResponseEntity<ErrorResponse> response =
                handler.handle(exception);

        assertThat(
                response.getStatusCode(),
                is(HttpStatus.UNPROCESSABLE_ENTITY)
        );
        assertThat(response.getBody(), is(notNullValue()));
        assertThat(response.getBody().errorCode(), is("VAL-422"));
        assertThat(response.getBody().message(), is("Invalid input"));
    }

    @Test
    void constraintViolationMapsTo422WithValCode() {
        ConstraintViolationException exception =
                mock(ConstraintViolationException.class);

        when(exception.getMessage())
                .thenReturn("invalid parameter");

        ResponseEntity<ErrorResponse> response =
                handler.handle(exception);

        assertThat(
                response.getStatusCode(),
                is(HttpStatus.UNPROCESSABLE_ENTITY)
        );
        assertThat(response.getBody(), is(notNullValue()));
        assertThat(response.getBody().errorCode(), is("VAL-422"));
        assertThat(response.getBody().message(), is("Invalid input"));
    }

    @Test
    void unreadableRequestBodyMapsTo422WithValCode() {
        HttpMessageNotReadableException exception =
                mock(HttpMessageNotReadableException.class);

        when(exception.getMessage())
                .thenReturn("invalid JSON");

        ResponseEntity<ErrorResponse> response =
                handler.handle(exception);

        assertThat(
                response.getStatusCode(),
                is(HttpStatus.UNPROCESSABLE_ENTITY)
        );
        assertThat(response.getBody(), is(notNullValue()));
        assertThat(response.getBody().errorCode(), is("VAL-422"));
        assertThat(response.getBody().message(), is("Invalid input"));
    }

    @Test
    void argumentTypeMismatchMapsTo422WithValCode() {
        MethodArgumentTypeMismatchException exception =
                mock(MethodArgumentTypeMismatchException.class);

        when(exception.getName())
                .thenReturn("id");

        ResponseEntity<ErrorResponse> response =
                handler.handle(exception);

        assertThat(
                response.getStatusCode(),
                is(HttpStatus.UNPROCESSABLE_ENTITY)
        );
        assertThat(response.getBody(), is(notNullValue()));
        assertThat(response.getBody().errorCode(), is("VAL-422"));
        assertThat(response.getBody().message(), is("Invalid input"));
    }

    @Test
    void missingRequestParameterMapsTo422WithValCode() {
        MissingServletRequestParameterException exception =
                new MissingServletRequestParameterException(
                        "accountId",
                        "Long"
                );

        ResponseEntity<ErrorResponse> response =
                handler.handle(exception);

        assertThat(
                response.getStatusCode(),
                is(HttpStatus.UNPROCESSABLE_ENTITY)
        );
        assertThat(response.getBody(), is(notNullValue()));
        assertThat(response.getBody().errorCode(), is("VAL-422"));
        assertThat(response.getBody().message(), is("Invalid input"));
    }

    // -------------------------------------------------------------------------
    // Transport errors
    // -------------------------------------------------------------------------

    @Test
    void noResourceFoundMapsTo404WithReqCode() {
        NoResourceFoundException exception =
                mock(NoResourceFoundException.class);

        when(exception.getResourcePath())
                .thenReturn("/does-not-exist");

        ResponseEntity<ErrorResponse> response =
                handler.handle(exception);

        assertThat(response.getStatusCode(), is(HttpStatus.NOT_FOUND));
        assertThat(response.getBody(), is(notNullValue()));
        assertThat(response.getBody().errorCode(), is("REQ-404"));
        assertThat(response.getBody().message(), is("Not found"));
    }

    @Test
    void unsupportedMethodMapsTo405WithReqCode() {
        HttpRequestMethodNotSupportedException exception =
                new HttpRequestMethodNotSupportedException("DELETE");

        ResponseEntity<ErrorResponse> response =
                handler.handle(exception);

        assertThat(
                response.getStatusCode(),
                is(HttpStatus.METHOD_NOT_ALLOWED)
        );
        assertThat(response.getBody(), is(notNullValue()));
        assertThat(response.getBody().errorCode(), is("REQ-405"));
        assertThat(response.getBody().message(), is("Method not allowed"));
    }

    @Test
    void unsupportedMediaTypeMapsTo415WithReqCode() {
        HttpMediaTypeNotSupportedException exception =
                new HttpMediaTypeNotSupportedException(
                        MediaType.TEXT_PLAIN_VALUE
                );

        ResponseEntity<ErrorResponse> response =
                handler.handle(exception);

        assertThat(
                response.getStatusCode(),
                is(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        );
        assertThat(response.getBody(), is(notNullValue()));
        assertThat(response.getBody().errorCode(), is("REQ-415"));
        assertThat(
                response.getBody().message(),
                is("Unsupported media type")
        );
    }

    // -------------------------------------------------------------------------
    // 500
    // -------------------------------------------------------------------------

    @Test
    void unmappedTradeExceptionMapsTo500() {
        TradeException exception =
                mock(TradeException.class);

        when(exception.catalogueCode())
                .thenReturn("TEST-500");

        when(exception.getMessage())
                .thenReturn("test error");

        ResponseEntity<ErrorResponse> response =
                handler.handleUnmapped(exception);

        assertThat(
                response.getStatusCode(),
                is(HttpStatus.INTERNAL_SERVER_ERROR)
        );
        assertThat(response.getBody(), is(notNullValue()));
        assertThat(response.getBody().errorCode(), is("TEST-500"));
    }

    @Test
    void unexpectedExceptionMapsTo500WithSrvCode() {
        Exception exception =
                new RuntimeException("database exploded");

        ResponseEntity<ErrorResponse> response =
                handler.handleUnexpected(exception);

        assertThat(
                response.getStatusCode(),
                is(HttpStatus.INTERNAL_SERVER_ERROR)
        );
        assertThat(response.getBody(), is(notNullValue()));
        assertThat(response.getBody().errorCode(), is("SRV-500"));
        assertThat(
                response.getBody().message(),
                is("Something went wrong")
        );
    }

    // -------------------------------------------------------------------------
    // Security / information leakage
    // -------------------------------------------------------------------------

    @Test
    void bodyNeverContainsInternalDetail() {
        ResponseEntity<ErrorResponse> response =
                handler.handle(
                        new InvalidOrderException("price", "-5.00")
                );

        assertThat(response.getBody(), is(notNullValue()));
        assertThat(
                response.getBody().message(),
                not(containsString("-5.00"))
        );
    }
}
