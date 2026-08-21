package com.luna.aggarly.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        int status,
        String message,
        T data,
        String errorCode,
        Map<String, String> errors,
        PaginationMeta meta,
        Instant timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return ok(data, "Operation completed successfully");
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                message,
                data,
                null,
                null,
                null,
                Instant.now()
        );
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return ok(data, message);
    }

    public static <T> ApiResponse<T> success(T data) {
        return ok(data);
    }

    public static <T> ApiResponse<T> created(T data) {
        return created(data, "Resource created successfully");
    }

    public static <T> ApiResponse<T> created(T data, String message) {
        return new ApiResponse<>(
                true,
                HttpStatus.CREATED.value(),
                message,
                data,
                null,
                null,
                null,
                Instant.now()
        );
    }

    public static <T> ApiResponse<T> accepted(String message) {
        return accepted(null, message);
    }

    public static <T> ApiResponse<T> accepted(T data, String message) {
        return new ApiResponse<>(
                true,
                HttpStatus.ACCEPTED.value(),
                message,
                data,
                null,
                null,
                null,
                Instant.now()
        );
    }

    public static <T> ApiResponse<T> noContent() {
        return noContent("Resource deleted or processed with no content");
    }

    public static <T> ApiResponse<T> noContent(String message) {
        return new ApiResponse<>(
                true,
                HttpStatus.NO_CONTENT.value(),
                message,
                null,
                null,
                null,
                null,
                Instant.now()
        );
    }

    public static <T> ApiResponse<T> empty(String message) {
        return new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                message,
                null,
                null,
                null,
                null,
                Instant.now()
        );
    }

    public static <T> ApiResponse<List<T>> paged(Page<T> page) {
        return paged(page, "Data retrieved successfully");
    }

    public static <T> ApiResponse<List<T>> paged(Page<T> page, String message) {
        return new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                message,
                page != null ? page.getContent() : List.of(),
                null,
                null,
                page != null ? PaginationMeta.fromPage(page) : null,
                Instant.now()
        );
    }

    public static <T> ApiResponse<List<T>> paged(List<T> content, PaginationMeta meta, String message) {
        return new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                message,
                content,
                null,
                null,
                meta,
                Instant.now()
        );
    }

    public static <T> ApiResponse<T> badRequest(String message, String errorCode) {
        return new ApiResponse<>(
                false,
                HttpStatus.BAD_REQUEST.value(),
                message,
                null,
                errorCode != null ? errorCode : "BAD_REQUEST",
                null,
                null,
                Instant.now()
        );
    }

    public static <T> ApiResponse<T> validationError(Map<String, String> errors, String message) {
        return new ApiResponse<>(
                false,
                HttpStatus.BAD_REQUEST.value(),
                message != null ? message : "Validation failed",
                null,
                "VALIDATION_ERROR",
                errors,
                null,
                Instant.now()
        );
    }

    public static <T> ApiResponse<T> unauthorized(String message) {
        return new ApiResponse<>(
                false,
                HttpStatus.UNAUTHORIZED.value(),
                message != null ? message : "Authentication required",
                null,
                "UNAUTHORIZED",
                null,
                null,
                Instant.now()
        );
    }

    public static <T> ApiResponse<T> forbidden(String message) {
        return new ApiResponse<>(
                false,
                HttpStatus.FORBIDDEN.value(),
                message != null ? message : "Access denied",
                null,
                "FORBIDDEN",
                null,
                null,
                Instant.now()
        );
    }

    public static <T> ApiResponse<T> notFound(String message) {
        return new ApiResponse<>(
                false,
                HttpStatus.NOT_FOUND.value(),
                message != null ? message : "Resource not found",
                null,
                "NOT_FOUND",
                null,
                null,
                Instant.now()
        );
    }

    public static <T> ApiResponse<T> notFound(String resourceName, Object identifier) {
        return notFound(String.format("%s not found with identifier: %s", resourceName, identifier));
    }

    public static <T> ApiResponse<T> conflict(String message, String errorCode) {
        return new ApiResponse<>(
                false,
                HttpStatus.CONFLICT.value(),
                message,
                null,
                errorCode != null ? errorCode : "CONFLICT",
                null,
                null,
                Instant.now()
        );
    }

    public static <T> ApiResponse<T> internalServerError(String message) {
        return new ApiResponse<>(
                false,
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                message != null ? message : "An unexpected server error occurred",
                null,
                "INTERNAL_SERVER_ERROR",
                null,
                null,
                Instant.now()
        );
    }

    public static <T> ApiResponse<T> error(
            int status,
            String message,
            String errorCode
    ) {
        return new ApiResponse<>(
                false,
                status,
                message,
                null,
                errorCode,
                null,
                null,
                Instant.now()
        );
    }

    public ResponseEntity<ApiResponse<T>> toResponseEntity() {
        return ResponseEntity.status(this.status).body(this);
    }

    public ResponseEntity<ApiResponse<T>> toResponseEntity(HttpStatus httpStatus) {
        return ResponseEntity.status(httpStatus).body(this);
    }
}