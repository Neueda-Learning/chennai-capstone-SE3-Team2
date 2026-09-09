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
import com.yellow.trade.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handle(AccountNotFoundException ex) {
        log.warn("account not found: {}", ex.requestedAccountId());
        return respond(HttpStatus.NOT_FOUND, "ACC-404", ex.getMessage());
    }

    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<ErrorResponse> handle(AccountNotActiveException ex) {
        log.warn("account not active: {}", ex.actualStatus());
        return respond(HttpStatus.FORBIDDEN, "ACC-403", ex.getMessage());
    }

    @ExceptionHandler(InstrumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handle(InstrumentNotFoundException ex) {
        log.warn("instrument not found: {} ({})", ex.requestedSymbol(), ex.reason());
        return respond(HttpStatus.NOT_FOUND, "INS-404", ex.getMessage());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handle(InsufficientFundsException ex) {
        log.warn("insufficient funds: required {}, available {}", ex.required(), ex.available());
        return respond(HttpStatus.BAD_REQUEST, "ORD-400", ex.getMessage());
    }

    @ExceptionHandler(InsufficientHoldingsException.class)
    public ResponseEntity<ErrorResponse> handle(InsufficientHoldingsException ex) {
        log.warn("insufficient holdings: requested {}, held {}", ex.requested(), ex.held());
        return respond(HttpStatus.CONFLICT, "ORD-409", ex.getMessage());
    }

    @ExceptionHandler(DuplicateOrderException.class)
    public ResponseEntity<ErrorResponse> handle(DuplicateOrderException ex) {
        log.warn("duplicate order: key {}", ex.idempotencyKey());
        return respond(HttpStatus.CONFLICT, "ORD-409", ex.getMessage());
    }

    // contract's own literal pairs 404 status with ORD-409 as the code here --
    // not a typo, the catalogue has no ORD-404. matches the contract exactly.
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handle(OrderNotFoundException ex) {
        log.warn("order not found: {}", ex.requestedOrderId());
        return respond(HttpStatus.NOT_FOUND, "ORD-409", ex.getMessage());
    }

    @ExceptionHandler(OrderNotCancellableException.class)
    public ResponseEntity<ErrorResponse> handle(OrderNotCancellableException ex) {
        log.warn("order not cancellable, status: {}", ex.actualStatus());
        return respond(HttpStatus.CONFLICT, "ORD-409", ex.getMessage());
    }

    // domain's own code here is "ORD-422", but the contract only allows VAL-422 --
    // outward code is overridden, not passed through from the exception
    @ExceptionHandler(InvalidOrderException.class)
    public ResponseEntity<ErrorResponse> handle(InvalidOrderException ex) {
        log.warn("invalid order field: {} = {}", ex.field(), ex.submittedValue());
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, "VAL-422", "Invalid input");
    }

    // thrown by @Valid on the PlaceOrderRequest body -- same VAL-422 as above,
    // just a different source (DTO annotations, not a domain rule)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handle(MethodArgumentNotValidException ex) {
        log.warn("request failed field validation: {}", ex.getMessage());
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, "VAL-422", "Invalid input");
    }

    private ResponseEntity<ErrorResponse> respond(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }
}