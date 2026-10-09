package com.yellow.trade.advice;

import com.yellow.trade.dto.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** A fund's refusal, answered by the module itself; everything else by the platform's handler. */
@RestControllerAdvice(assignableTypes = AdviceController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class AdviceExceptionHandler {

    @ExceptionHandler(AdviceExceptions.FundSignalException.class)
    ResponseEntity<ErrorResponse> handle(AdviceExceptions.FundSignalException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorResponse(e.catalogueCode(), e.getMessage()));
    }
}
