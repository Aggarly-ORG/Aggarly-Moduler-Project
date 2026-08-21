package com.luna.aggarly.payment.exceptions;

import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.common.exceptions.AggarlyException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PaymentExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentNotFound(PaymentNotFoundException ex, HttpServletRequest request) {
        log.warn("PaymentNotFoundException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(ex.getStatus().value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }

    @ExceptionHandler(PaymentAlreadyProcessedException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentAlreadyProcessed(PaymentAlreadyProcessedException ex, HttpServletRequest request) {
        log.warn("PaymentAlreadyProcessedException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(ex.getStatus().value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleIdempotencyConflict(IdempotencyConflictException ex, HttpServletRequest request) {
        log.warn("IdempotencyConflictException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(ex.getStatus().value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }

    @ExceptionHandler(IllegalPaymentStateTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalPaymentStateTransition(IllegalPaymentStateTransitionException ex, HttpServletRequest request) {
        log.warn("IllegalPaymentStateTransitionException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(ex.getStatus().value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }

    @ExceptionHandler(RefundExceedsPaymentException.class)
    public ResponseEntity<ApiResponse<Void>> handleRefundExceedsPayment(RefundExceedsPaymentException ex, HttpServletRequest request) {
        log.warn("RefundExceedsPaymentException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(ex.getStatus().value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }

    @ExceptionHandler(RefundFailedException.class)
    public ResponseEntity<ApiResponse<Void>> handleRefundFailed(RefundFailedException ex, HttpServletRequest request) {
        log.warn("RefundFailedException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(ex.getStatus().value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }

    @ExceptionHandler(StripeWebhookException.class)
    public ResponseEntity<ApiResponse<Void>> handleStripeWebhook(StripeWebhookException ex, HttpServletRequest request) {
        log.warn("StripeWebhookException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(ex.getStatus().value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }
}
