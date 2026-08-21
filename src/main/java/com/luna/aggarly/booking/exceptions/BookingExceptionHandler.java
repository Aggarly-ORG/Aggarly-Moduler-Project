package com.luna.aggarly.booking.exceptions;

import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.common.exceptions.AggarlyException;
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
public class BookingExceptionHandler {

    @ExceptionHandler(BookingNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleBookingNotFound(BookingNotFoundException ex, HttpServletRequest request) {
        log.warn("BookingNotFoundException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.NOT_FOUND.value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }

    @ExceptionHandler(UnauthorizedBookingAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorizedBookingAccess(UnauthorizedBookingAccessException ex, HttpServletRequest request) {
        log.warn("UnauthorizedBookingAccessException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.FORBIDDEN.value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }

    @ExceptionHandler(InvalidBookingStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidBookingState(InvalidBookingStateException ex, HttpServletRequest request) {
        log.warn("InvalidBookingStateException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.CONFLICT.value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }

    @ExceptionHandler(BookingCreationFailedException.class)
    public ResponseEntity<ApiResponse<Void>> handleBookingCreationFailed(BookingCreationFailedException ex, HttpServletRequest request) {
        log.warn("BookingCreationFailedException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.CONFLICT.value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }
}
