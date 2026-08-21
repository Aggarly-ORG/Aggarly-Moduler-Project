package com.luna.aggarly.payment.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class RefundFailedException extends AggarlyException {
    public RefundFailedException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, "REFUND_FAILED");
    }
}
