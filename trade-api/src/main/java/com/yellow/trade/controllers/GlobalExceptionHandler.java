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

    // ---------------------------------------------------------------- 404

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handle(AccountNotFoundException e) {
        log.warn("ACC-404: no account with key {}", e.requestedAccountId());
        return envelope(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(InstrumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handle(InstrumentNotFoundException e) {
        // The typed reason separates "no such symbol" from "delisted last
        // month", which the client must not be able to tell apart but an
        // investigation needs.
        log.warn("INS-404: symbol {} ({})", e.requestedSymbol(), e.reason());
        return envelope(HttpStatus.NOT_FOUND, e);
    }

    // ---------------------------------------------------------------- 403

    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<ErrorResponse> handle(AccountNotActiveException e) {
        log.warn("ACC-403: account status {}", e.actualStatus());
        return envelope(HttpStatus.FORBIDDEN, e);
    }

    // ---------------------------------------------------------------- 400

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handle(InsufficientFundsException e) {
        log.warn("ORD-400: required {}, available {}", e.required(), e.available());
        return envelope(HttpStatus.BAD_REQUEST, e);
    }

    // ---------------------------------------------------------------- 409

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

    /**
     * 404 with the ORD-409 code, which is what the contract states and is not a
     * typo: the catalogue has no ORD-404, so an order that does not exist
     * borrows the conflict code while answering the not-found status.
     *
     * This is the one place a status and a code disagree in shape, and it is
     * why the contract insists clients branch on errorCode rather than on the
     * status alone.
     */
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handle(OrderNotFoundException e) {
        log.warn("order not found: {}", e.requestedOrderId());
        return envelope(HttpStatus.NOT_FOUND, e);
    }

    // ---------------------------------------------------------------- 422

    @ExceptionHandler(InvalidOrderException.class)
    public ResponseEntity<ErrorResponse> handle(InvalidOrderException e) {
        log.warn("VAL-422: field {} was {}", e.field(), e.submittedValue());
        return envelope(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    /** @Valid on the request body. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handle(MethodArgumentNotValidException e) {
        log.warn("VAL-422: body failed validation: {}", e.getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage()).toList());
        return envelope(HttpStatus.UNPROCESSABLE_ENTITY, "VAL-422", "Invalid input");
    }

    /** @Min and friends on a path variable or request parameter. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handle(ConstraintViolationException e) {
        log.warn("VAL-422: parameter failed validation: {}", e.getMessage());
        return envelope(HttpStatus.UNPROCESSABLE_ENTITY, "VAL-422", "Invalid input");
    }

    /**
     * A body that is not JSON, or is JSON of the wrong shape, or carries a
     * field the contract does not declare. Without this, Spring answers 400
     * with its own body and the envelope has a hole in it.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handle(HttpMessageNotReadableException e) {
        // getMessage() can quote the offending JSON. It goes to the log only.
        log.warn("VAL-422: unreadable request body: {}", e.getMessage());
        return envelope(HttpStatus.UNPROCESSABLE_ENTITY, "VAL-422", "Invalid input");
    }

    /**
     * ?status=NOPE, or an order id that is not a UUID. Spring's default answer
     * is a 500, which is both wrong and outside the envelope.
     */
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

    // ------------------------------------------------- transport mismatches
    //
    // These are not in the catalogue, because the catalogue describes business
    // outcomes and these are a client addressing the service wrongly. They
    // still leave as the envelope: the Angular error handler must never meet a
    // body it cannot parse.

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

    // ---------------------------------------------------------------- 500

    /**
     * A domain exception nobody wrote a handler for.
     *
     * It cannot be mapped to a status from here, so it is a 500 -- but it
     * still leaves as its own catalogue code, because the domain author put
     * one on it and a client can branch on that. Reaching this method means a
     * type was added to the domain and this class was not updated.
     */
    @ExceptionHandler(TradeException.class)
    public ResponseEntity<ErrorResponse> handleUnmapped(TradeException e) {
        log.error("unmapped domain exception {} carrying {}",
                e.getClass().getSimpleName(), e.catalogueCode(), e);
        return envelope(HttpStatus.INTERNAL_SERVER_ERROR, e);
    }

    /**
     * The backstop. Anything at all -- a null dereference, a driver failure, a
     * connection timeout -- leaves as the envelope rather than as a whitelabel
     * page or a bare status with an empty body.
     *
     * The stack trace goes to the log, at ERROR, with the exception attached.
     * The client gets a code and a sentence, because the alternative is
     * handing an attacker the class names and line numbers of the internals.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("unhandled exception", e);
        return envelope(HttpStatus.INTERNAL_SERVER_ERROR, "SRV-500", "Something went wrong");
    }

    // ----------------------------------------------------------------------

    /**
     * The code and the message both come from the exception, which is where
     * the domain put them. Sprint 7 maps the same code onto a Kafka rejection
     * reason, which is why the domain carries a catalogue code and never an
     * HTTP status.
     */
    private ResponseEntity<ErrorResponse> envelope(HttpStatus status, TradeException e) {
        return envelope(status, e.catalogueCode(), e.getMessage());
    }

    private ResponseEntity<ErrorResponse> envelope(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }
}
