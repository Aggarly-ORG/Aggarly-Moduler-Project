package com.luna.aggarly.payment.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class PaymentAlreadyProcessedException extends AggarlyException {
    public PaymentAlreadyProcessedException(String message) {
        super(message, HttpStatus.CONFLICT, "PAYMENT_ALREADY_PROCESSED");
    }
}
