package com.luna.aggarly.property.exceptions;

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
public class PropertyExceptionHandler {

    @ExceptionHandler(PropertyNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handlePropertyNotFound(PropertyNotFoundException ex, HttpServletRequest request) {
        log.warn("PropertyNotFoundException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.NOT_FOUND.value(), ex.getMessage(), "PROPERTY_NOT_FOUND").toResponseEntity();
    }

    @ExceptionHandler(UnauthorizedPropertyAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorizedAccess(UnauthorizedPropertyAccessException ex, HttpServletRequest request) {
        log.warn("UnauthorizedPropertyAccessException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.FORBIDDEN.value(), ex.getMessage(), "UNAUTHORIZED_PROPERTY_ACCESS").toResponseEntity();
    }
}
