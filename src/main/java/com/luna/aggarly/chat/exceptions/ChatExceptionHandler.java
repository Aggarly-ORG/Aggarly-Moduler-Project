package com.luna.aggarly.chat.exceptions;

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
public class ChatExceptionHandler {

    @ExceptionHandler(ConversationNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleConversationNotFound(ConversationNotFoundException ex, HttpServletRequest request) {
        log.warn("ConversationNotFoundException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.NOT_FOUND.value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }

    @ExceptionHandler(UnauthorizedChatAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorizedAccess(UnauthorizedChatAccessException ex, HttpServletRequest request) {
        log.warn("UnauthorizedChatAccessException on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ApiResponse.<Void>error(HttpStatus.FORBIDDEN.value(), ex.getMessage(), ex.getErrorCode()).toResponseEntity();
    }
}
