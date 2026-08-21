package com.luna.aggarly.notification.exceptions;

import com.luna.aggarly.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NotificationExceptionHandler {

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotificationNotFound(NotificationNotFoundException ex, HttpServletRequest request) {
        log.warn("NotificationNotFoundException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.NOT_FOUND.value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }
}
