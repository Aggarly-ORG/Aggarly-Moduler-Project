package com.luna.aggarly.payment.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class IllegalPaymentStateTransitionException extends AggarlyException {
    public IllegalPaymentStateTransitionException(String message) {
        super(message, HttpStatus.CONFLICT, "ILLEGAL_PAYMENT_STATE_TRANSITION");
    }
}
