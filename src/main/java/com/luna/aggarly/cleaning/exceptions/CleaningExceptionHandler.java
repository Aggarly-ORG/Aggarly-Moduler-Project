package com.luna.aggarly.cleaning.exceptions;

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
public class CleaningExceptionHandler {

    @ExceptionHandler(CleaningTaskNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleTaskNotFound(CleaningTaskNotFoundException ex, HttpServletRequest request) {
        log.warn("CleaningTaskNotFoundException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.NOT_FOUND.value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }

    @ExceptionHandler(InvalidCleaningStatusTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidTransition(InvalidCleaningStatusTransitionException ex, HttpServletRequest request) {
        log.warn("InvalidCleaningStatusTransitionException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.CONFLICT.value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }

    @ExceptionHandler(UnauthorizedCleanerAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorizedAccess(UnauthorizedCleanerAccessException ex, HttpServletRequest request) {
        log.warn("UnauthorizedCleanerAccessException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.FORBIDDEN.value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }

    @ExceptionHandler(CleaningChecklistNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleChecklistNotFound(CleaningChecklistNotFoundException ex, HttpServletRequest request) {
        log.warn("CleaningChecklistNotFoundException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.NOT_FOUND.value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }

    @ExceptionHandler(CleaningIssueNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleIssueNotFound(CleaningIssueNotFoundException ex, HttpServletRequest request) {
        log.warn("CleaningIssueNotFoundException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.NOT_FOUND.value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }
}
