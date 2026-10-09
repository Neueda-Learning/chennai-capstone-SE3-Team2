package com.yellow.trade.strategy;

import com.yellow.exceptions.TradeException;
import com.yellow.trade.dto.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** The errors this module adds, answered by the module itself; everything else by the platform's handler. */
@RestControllerAdvice(assignableTypes = StrategyController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class StrategyExceptionHandler {

    @ExceptionHandler(StrategyExceptions.NotFoundException.class)
    ResponseEntity<ErrorResponse> handle(StrategyExceptions.NotFoundException e) {
        return envelope(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(StrategyExceptions.LimitReachedException.class)
    ResponseEntity<ErrorResponse> handle(StrategyExceptions.LimitReachedException e) {
        return envelope(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(StrategyExceptions.FundStrategyException.class)
    ResponseEntity<ErrorResponse> handle(StrategyExceptions.FundStrategyException e) {
        return envelope(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    @ExceptionHandler(StrategyExceptions.TriggerPriceException.class)
    ResponseEntity<ErrorResponse> handle(StrategyExceptions.TriggerPriceException e) {
        return envelope(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    private static ResponseEntity<ErrorResponse> envelope(HttpStatus status, TradeException e) {
        return ResponseEntity.status(status).body(new ErrorResponse(e.catalogueCode(), e.getMessage()));
    }
}
