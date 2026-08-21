package com.luna.aggarly.user.exceptions;

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
public class UserExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserNotFound(UserNotFoundException ex, HttpServletRequest request) {
        log.warn("UserNotFoundException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.NOT_FOUND.value(), ex.getMessage(), "USER_NOT_FOUND").toResponseEntity();
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmailExists(EmailAlreadyExistsException ex, HttpServletRequest request) {
        log.warn("EmailAlreadyExistsException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.CONFLICT.value(), ex.getMessage(), "EMAIL_EXISTS").toResponseEntity();
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCredentials(InvalidCredentialsException ex, HttpServletRequest request) {
        log.warn("InvalidCredentialsException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.UNAUTHORIZED.value(), ex.getMessage(), "INVALID_CREDENTIALS").toResponseEntity();
    }

    @ExceptionHandler(InvalidMfaTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidMfaToken(InvalidMfaTokenException ex, HttpServletRequest request) {
        log.warn("InvalidMfaTokenException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.UNAUTHORIZED.value(), ex.getMessage(), "INVALID_MFA_TOKEN").toResponseEntity();
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRefreshToken(InvalidRefreshTokenException ex, HttpServletRequest request) {
        log.warn("InvalidRefreshTokenException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.UNAUTHORIZED.value(), ex.getMessage(), "INVALID_REFRESH_TOKEN").toResponseEntity();
    }

    @ExceptionHandler(InvalidTotpException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidTotp(InvalidTotpException ex, HttpServletRequest request) {
        log.warn("InvalidTotpException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.UNAUTHORIZED.value(), ex.getMessage(), "INVALID_TOTP").toResponseEntity();
    }

    @ExceptionHandler(VerificationException.class)
    public ResponseEntity<ApiResponse<Void>> handleVerification(VerificationException ex, HttpServletRequest request) {
        log.warn("VerificationException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), "VERIFICATION_ERROR").toResponseEntity();
    }
}
