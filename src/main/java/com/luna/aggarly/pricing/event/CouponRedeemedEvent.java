package com.luna.aggarly.pricing.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class CouponRedeemedEvent extends ApplicationEvent {
    private final UUID couponId;
    private final UUID userId;
    private final UUID bookingId;

    public CouponRedeemedEvent(Object source, UUID couponId, UUID userId, UUID bookingId) {
        super(source);
        this.couponId = couponId;
        this.userId = userId;
        this.bookingId = bookingId;
    }
}
