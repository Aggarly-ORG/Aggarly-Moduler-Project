package com.luna.aggarly.payment.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class PaymentFailedEvent extends ApplicationEvent {
    private final UUID bookingId;
    private final UUID paymentId;
    private final String errorCode;
    private final String errorMessage;

    public PaymentFailedEvent(Object source, UUID bookingId, UUID paymentId, String errorCode, String errorMessage) {
        super(source);
        this.bookingId = bookingId;
        this.paymentId = paymentId;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
}
