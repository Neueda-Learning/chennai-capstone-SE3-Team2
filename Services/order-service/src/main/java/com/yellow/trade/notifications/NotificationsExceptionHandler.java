package com.yellow.trade.notifications;

import com.yellow.trade.dto.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The one error this module adds, answered by the module itself, so the
 * platform's GlobalExceptionHandler never reaches into it. Only this module's
 * controller; ahead of the global handler, which answers everything else.
 */
@RestControllerAdvice(assignableTypes = NotificationsController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class NotificationsExceptionHandler {

    @ExceptionHandler(NotificationNotFoundException.class)
    ResponseEntity<ErrorResponse> handle(NotificationNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.catalogueCode(), e.getMessage()));
    }
}
