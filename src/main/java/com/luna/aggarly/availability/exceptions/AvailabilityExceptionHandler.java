package com.luna.aggarly.availability.exceptions;

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
public class AvailabilityExceptionHandler {

    @ExceptionHandler(AvailabilitySlotNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleAvailabilitySlotNotFound(AvailabilitySlotNotFoundException ex, HttpServletRequest request) {
        log.warn("AvailabilitySlotNotFoundException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.NOT_FOUND.value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }

    @ExceptionHandler(DateRangeOverlapException.class)
    public ResponseEntity<ApiResponse<Void>> handleDateRangeOverlap(DateRangeOverlapException ex, HttpServletRequest request) {
        log.warn("DateRangeOverlapException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.CONFLICT.value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }

    @ExceptionHandler(InvalidDateRangeException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidDateRange(InvalidDateRangeException ex, HttpServletRequest request) {
        log.warn("InvalidDateRangeException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }
}
