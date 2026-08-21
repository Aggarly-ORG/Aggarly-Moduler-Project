package com.luna.aggarly.payment.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class PaymentNotFoundException extends AggarlyException {
    public PaymentNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND");
    }
}
