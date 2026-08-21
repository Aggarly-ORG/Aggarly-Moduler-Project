package com.luna.aggarly.pricing.exceptions;

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
public class PricingExceptionHandler {

    @ExceptionHandler(InvalidCouponException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCouponException(InvalidCouponException ex, HttpServletRequest request) {
        log.warn("InvalidCouponException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }

    @ExceptionHandler(CouponExpiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleCouponExpiredException(CouponExpiredException ex, HttpServletRequest request) {
        log.warn("CouponExpiredException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }

    @ExceptionHandler(CouponAlreadyRedeemedException.class)
    public ResponseEntity<ApiResponse<Void>> handleCouponAlreadyRedeemedException(CouponAlreadyRedeemedException ex, HttpServletRequest request) {
        log.warn("CouponAlreadyRedeemedException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.CONFLICT.value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }

    @ExceptionHandler(PricingRuleConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handlePricingRuleConflictException(PricingRuleConflictException ex, HttpServletRequest request) {
        log.warn("PricingRuleConflictException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.CONFLICT.value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }

    @ExceptionHandler(PricingRuleNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handlePricingRuleNotFoundException(PricingRuleNotFoundException ex, HttpServletRequest request) {
        log.warn("PricingRuleNotFoundException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.NOT_FOUND.value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }
}
