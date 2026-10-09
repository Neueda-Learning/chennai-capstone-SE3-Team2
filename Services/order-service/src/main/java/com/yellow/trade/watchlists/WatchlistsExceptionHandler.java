package com.yellow.trade.watchlists;

import com.yellow.exceptions.TradeException;
import com.yellow.trade.dto.ErrorResponse;
import com.yellow.trade.watchlists.WatchExceptions.FundAlertException;
import com.yellow.trade.watchlists.WatchExceptions.LimitReachedException;
import com.yellow.trade.watchlists.WatchExceptions.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The errors this module adds, answered by the module itself, so the
 * platform's GlobalExceptionHandler never reaches into it. Only this module's
 * controllers; ahead of the global handler, which answers everything else.
 */
@RestControllerAdvice(assignableTypes = {WatchlistsController.class, AlertsController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
class WatchlistsExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(WatchlistsExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ErrorResponse> handle(NotFoundException e) {
        return envelope(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(LimitReachedException.class)
    ResponseEntity<ErrorResponse> handle(LimitReachedException e) {
        log.warn("{}: {}", e.catalogueCode(), e.getMessage());
        return envelope(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(FundAlertException.class)
    ResponseEntity<ErrorResponse> handle(FundAlertException e) {
        return envelope(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    private static ResponseEntity<ErrorResponse> envelope(HttpStatus status, TradeException e) {
        return ResponseEntity.status(status).body(new ErrorResponse(e.catalogueCode(), e.getMessage()));
    }
}
