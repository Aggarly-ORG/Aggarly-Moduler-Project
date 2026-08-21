package com.luna.aggarly.common.exceptions;

import com.luna.aggarly.common.dto.ApiResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AggarlyException.class)
    public ResponseEntity<ApiResponse<Void>> handleAggarlyException(AggarlyException ex, HttpServletRequest request) {
        log.warn("AggarlyException on path [{}]: status={}, code={}, message={}",
                request.getRequestURI(), ex.getStatus(), ex.getErrorCode(), ex.getMessage());
        return ApiResponse.<Void>error(ex.getStatus().value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleEntityNotFoundException(EntityNotFoundException ex, HttpServletRequest request) {
        log.warn("EntityNotFoundException on path [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>notFound(ex.getMessage()).toResponseEntity();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        log.warn("Validation failed on path [{}] for fields: {}", request.getRequestURI(), errors.keySet());
        return ApiResponse.<Void>validationError(errors, "Validation failed for request payload").toResponseEntity();
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String property = violation.getPropertyPath() != null ? violation.getPropertyPath().toString() : "param";
            errors.put(property, violation.getMessage());
        }
        log.warn("Constraint violation on path [{}]: {}", request.getRequestURI(), errors);
        return ApiResponse.<Void>validationError(errors, "Constraint validation failed").toResponseEntity();
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("AccessDeniedException on path [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>forbidden("Access denied: insufficient permissions to access this resource").toResponseEntity();
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        log.warn("AuthenticationException on path [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>unauthorized("Authentication required: " + ex.getMessage()).toResponseEntity();
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalStateException(IllegalStateException ex, HttpServletRequest request) {
        log.warn("IllegalStateException on path [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>conflict(ex.getMessage(), "ILLEGAL_STATE").toResponseEntity();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("IllegalArgumentException on path [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>badRequest(ex.getMessage(), "INVALID_ARGUMENT").toResponseEntity();
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParams(MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.warn("Missing parameter on path [{}]: {}", request.getRequestURI(), ex.getParameterName());
        return ApiResponse.<Void>badRequest("Required request parameter '" + ex.getParameterName() + "' is missing", "MISSING_PARAMETER").toResponseEntity();
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.warn("Type mismatch on path [{}]: param={}, value={}", request.getRequestURI(), ex.getName(), ex.getValue());
        String msg = String.format("Parameter '%s' with value '%s' could not be converted to expected type", ex.getName(), ex.getValue());
        return ApiResponse.<Void>badRequest(msg, "TYPE_MISMATCH").toResponseEntity();
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        log.warn("Method not supported on path [{}]: {}", request.getRequestURI(), ex.getMethod());
        return ApiResponse.<Void>error(HttpStatus.METHOD_NOT_ALLOWED.value(), "HTTP method " + ex.getMethod() + " is not supported for this route", "METHOD_NOT_ALLOWED").toResponseEntity();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on path [{}]: {}", request.getRequestURI(), ex.getMessage(), ex);
        return ApiResponse.<Void>internalServerError("An unexpected internal server error occurred").toResponseEntity();
    }
}
