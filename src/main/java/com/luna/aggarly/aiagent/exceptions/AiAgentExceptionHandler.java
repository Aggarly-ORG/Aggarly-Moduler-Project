package com.luna.aggarly.aiagent.exceptions;

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
public class AiAgentExceptionHandler {

    @ExceptionHandler(ConfirmationRequiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleConfirmationRequired(ConfirmationRequiredException ex, HttpServletRequest request) {
        log.warn("ConfirmationRequiredException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.PRECONDITION_REQUIRED.value(), ex.getMessage(), "CONFIRMATION_REQUIRED").toResponseEntity();
    }

    @ExceptionHandler(UnauthorizedToolAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorizedToolAccess(UnauthorizedToolAccessException ex, HttpServletRequest request) {
        log.warn("UnauthorizedToolAccessException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.FORBIDDEN.value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }

    @ExceptionHandler(ToolExecutionException.class)
    public ResponseEntity<ApiResponse<Void>> handleToolExecution(ToolExecutionException ex, HttpServletRequest request) {
        log.warn("ToolExecutionException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.INTERNAL_SERVER_ERROR.value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }
}
