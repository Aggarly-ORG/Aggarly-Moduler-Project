package com.luna.aggarly.pricing.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class CouponExpiredException extends AggarlyException {
    public CouponExpiredException(String code) {
        super("Coupon expired: " + code, HttpStatus.BAD_REQUEST, "COUPON_EXPIRED");
    }
}
