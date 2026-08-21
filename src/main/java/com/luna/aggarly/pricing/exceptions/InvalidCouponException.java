package com.luna.aggarly.pricing.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class InvalidCouponException extends AggarlyException {
    public InvalidCouponException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "INVALID_COUPON");
    }
}
