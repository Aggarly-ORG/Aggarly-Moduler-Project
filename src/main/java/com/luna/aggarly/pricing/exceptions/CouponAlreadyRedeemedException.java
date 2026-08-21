package com.luna.aggarly.pricing.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class CouponAlreadyRedeemedException extends AggarlyException {
    public CouponAlreadyRedeemedException(String code) {
        super("Coupon already redeemed by this user: " + code, HttpStatus.CONFLICT, "COUPON_ALREADY_REDEEMED");
    }
}
