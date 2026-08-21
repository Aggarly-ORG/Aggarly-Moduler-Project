package com.luna.aggarly.filestorage.exceptions;

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
public class FileStorageExceptionHandler {

    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileStorageException(FileStorageException ex, HttpServletRequest request) {
        log.warn("FileStorageException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.INTERNAL_SERVER_ERROR.value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }

    @ExceptionHandler(FileNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileNotFoundException(FileNotFoundException ex, HttpServletRequest request) {
        log.warn("FileNotFoundException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.NOT_FOUND.value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }

    @ExceptionHandler(StorageUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleStorageUnavailableException(StorageUnavailableException ex, HttpServletRequest request) {
        log.warn("StorageUnavailableException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.SERVICE_UNAVAILABLE.value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }

    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidFileException(InvalidFileException ex, HttpServletRequest request) {
        log.warn("InvalidFileException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }

    @ExceptionHandler(FileAlreadyDeletedException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileAlreadyDeletedException(FileAlreadyDeletedException ex, HttpServletRequest request) {
        log.warn("FileAlreadyDeletedException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.GONE.value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }
}
