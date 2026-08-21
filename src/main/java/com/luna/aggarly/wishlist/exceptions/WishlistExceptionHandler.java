package com.luna.aggarly.wishlist.exceptions;

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
public class WishlistExceptionHandler {

    @ExceptionHandler(WishlistNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleWishlistNotFound(WishlistNotFoundException ex, HttpServletRequest request) {
        log.warn("WishlistNotFoundException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.NOT_FOUND.value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }

    @ExceptionHandler(DuplicateWishlistItemException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateWishlistItem(DuplicateWishlistItemException ex, HttpServletRequest request) {
        log.warn("DuplicateWishlistItemException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.CONFLICT.value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }
}
