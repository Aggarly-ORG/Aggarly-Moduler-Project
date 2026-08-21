package com.luna.aggarly.payment.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class RefundFailedEvent extends ApplicationEvent {
    private final UUID bookingId;
    private final UUID refundId;
    private final String errorCode;

    public RefundFailedEvent(Object source, UUID bookingId, UUID refundId, String errorCode) {
        super(source);
        this.bookingId = bookingId;
        this.refundId = refundId;
        this.errorCode = errorCode;
    }
}
