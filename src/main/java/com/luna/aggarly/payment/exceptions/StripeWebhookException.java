package com.luna.aggarly.payment.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class StripeWebhookException extends AggarlyException {
    public StripeWebhookException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "STRIPE_WEBHOOK_ERROR");
    }
}
