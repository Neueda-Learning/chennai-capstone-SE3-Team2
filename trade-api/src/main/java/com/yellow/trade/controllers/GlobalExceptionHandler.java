package com.yellow.trade.controllers;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handle(AccountNotFoundException e) {
        log.warn("ACC-404: no account with key {}", e.requestedAccountId());
        return envelope(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(InstrumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handle(InstrumentNotFoundException e) {
        log.warn("INS-404: symbol {} ({})", e.requestedSymbol(), e.reason());
        return envelope(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<ErrorResponse> handle(AccountNotActiveException e) {
        log.warn("ACC-403: account status {}", e.actualStatus());
        return envelope(HttpStatus.FORBIDDEN, e);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handle(InsufficientFundsException e) {
        log.warn("ORD-400: required {}, available {}", e.required(), e.available());
        return envelope(HttpStatus.BAD_REQUEST, e);
    }

    @ExceptionHandler(InsufficientHoldingsException.class)
    public ResponseEntity<ErrorResponse> handle(InsufficientHoldingsException e) {
        log.warn("ORD-409: sell of {} against holding of {}", e.requested(), e.held());
        return envelope(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(DuplicateOrderException.class)
    public ResponseEntity<ErrorResponse> handle(DuplicateOrderException e) {
        log.warn("ORD-409: idempotency key {} already accepted", e.idempotencyKey());
        return envelope(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(OrderNotCancellableException.class)
    public ResponseEntity<ErrorResponse> handle(OrderNotCancellableException e) {
        log.warn("ORD-409: order was {} when cancel ran", e.actualStatus());
        return envelope(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(StaleAccountVersionException.class)
    public ResponseEntity<ErrorResponse> handle(StaleAccountVersionException e) {
        log.warn("ORD-409: account {} moved past version {} before the write landed",
                e.accountId(), e.expectedVersion());
        return envelope(HttpStatus.CONFLICT, e);
    }


    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handle(OrderNotFoundException e) {
        log.warn("order not found: {}", e.requestedOrderId());
        return envelope(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(InvalidOrderException.class)
    public ResponseEntity<ErrorResponse> handle(InvalidOrderException e) {
        log.warn("VAL-422: field {} was {}", e.field(), e.submittedValue());
        return envelope(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handle(MethodArgumentNotValidException e) {
        log.warn("VAL-422: body failed validation: {}", e.getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage()).toList());
        return envelope(HttpStatus.UNPROCESSABLE_ENTITY, "VAL-422", "Invalid input");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handle(ConstraintViolationException e) {
        log.warn("VAL-422: parameter failed validation: {}", e.getMessage());
        return envelope(HttpStatus.UNPROCESSABLE_ENTITY, "VAL-422", "Invalid input");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handle(HttpMessageNotReadableException e) {
        log.warn("VAL-422: unreadable request body: {}", e.getMessage());
        return envelope(HttpStatus.UNPROCESSABLE_ENTITY, "VAL-422", "Invalid input");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handle(MethodArgumentTypeMismatchException e) {
        log.warn("VAL-422: parameter {} could not be read as {}",
                e.getName(), e.getRequiredType() == null ? "?" : e.getRequiredType().getSimpleName());
        return envelope(HttpStatus.UNPROCESSABLE_ENTITY, "VAL-422", "Invalid input");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handle(MissingServletRequestParameterException e) {
        log.warn("VAL-422: missing parameter {}", e.getParameterName());
        return envelope(HttpStatus.UNPROCESSABLE_ENTITY, "VAL-422", "Invalid input");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handle(NoResourceFoundException e) {
        log.warn("no route for {}", e.getResourcePath());
        return envelope(HttpStatus.NOT_FOUND, "REQ-404", "Not found");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handle(HttpRequestMethodNotSupportedException e) {
        log.warn("method {} not supported on this route", e.getMethod());
        return envelope(HttpStatus.METHOD_NOT_ALLOWED, "REQ-405", "Method not allowed");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handle(HttpMediaTypeNotSupportedException e) {
        log.warn("unsupported content type {}", e.getContentType());
        return envelope(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "REQ-415", "Unsupported media type");
    }

    @ExceptionHandler(TradeException.class)
    public ResponseEntity<ErrorResponse> handleUnmapped(TradeException e) {
        log.error("unmapped domain exception {} carrying {}",
                e.getClass().getSimpleName(), e.catalogueCode(), e);
        return envelope(HttpStatus.INTERNAL_SERVER_ERROR, e);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("unhandled exception", e);
        return envelope(HttpStatus.INTERNAL_SERVER_ERROR, "SRV-500", "Something went wrong");
    }

    private ResponseEntity<ErrorResponse> envelope(HttpStatus status, TradeException e) {
        return envelope(status, e.catalogueCode(), e.getMessage());
    }

    private ResponseEntity<ErrorResponse> envelope(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }
}
