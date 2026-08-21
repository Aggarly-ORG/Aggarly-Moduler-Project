package com.luna.aggarly.payment.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class IdempotencyConflictException extends AggarlyException {
    public IdempotencyConflictException(String message) {
        super(message, HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT");
    }
}
