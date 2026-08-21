package com.luna.aggarly.review.exceptions;

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
public class ReviewExceptionHandler {

    @ExceptionHandler(ReviewNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleReviewNotFound(ReviewNotFoundException ex, HttpServletRequest request) {
        log.warn("ReviewNotFoundException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.NOT_FOUND.value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }

    @ExceptionHandler(AlreadyReviewedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAlreadyReviewed(AlreadyReviewedException ex, HttpServletRequest request) {
        log.warn("AlreadyReviewedException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.CONFLICT.value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }

    @ExceptionHandler(UncompletedStayReviewException.class)
    public ResponseEntity<ApiResponse<Void>> handleUncompletedStayReview(UncompletedStayReviewException ex, HttpServletRequest request) {
        log.warn("UncompletedStayReviewException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.FORBIDDEN.value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }
}
