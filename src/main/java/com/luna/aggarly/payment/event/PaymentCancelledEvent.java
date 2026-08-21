package com.luna.aggarly.payment.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class PaymentCancelledEvent extends ApplicationEvent {
    private final UUID bookingId;
    private final UUID paymentId;

    public PaymentCancelledEvent(Object source, UUID bookingId, UUID paymentId) {
        super(source);
        this.bookingId = bookingId;
        this.paymentId = paymentId;
    }
}
