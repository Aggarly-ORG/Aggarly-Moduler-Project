package com.luna.aggarly.payment.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class DisputeClosedEvent extends ApplicationEvent {
    private final UUID bookingId;
    private final UUID paymentId;
    private final String outcome; // won or lost

    public DisputeClosedEvent(Object source, UUID bookingId, UUID paymentId, String outcome) {
        super(source);
        this.bookingId = bookingId;
        this.paymentId = paymentId;
        this.outcome = outcome;
    }
}
