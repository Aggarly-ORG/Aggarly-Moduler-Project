package com.luna.aggarly.payment.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class RefundExceedsPaymentException extends AggarlyException {
    public RefundExceedsPaymentException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "REFUND_EXCEEDS_PAYMENT");
    }
}
