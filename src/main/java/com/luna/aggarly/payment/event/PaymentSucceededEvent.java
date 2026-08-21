package com.luna.aggarly.payment.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
public class PaymentSucceededEvent extends ApplicationEvent {
    private final UUID bookingId;
    private final UUID paymentId;
    private final BigDecimal amount;
    private final Instant paidAt;

    public PaymentSucceededEvent(Object source, UUID bookingId, UUID paymentId, BigDecimal amount, Instant paidAt) {
        super(source);
        this.bookingId = bookingId;
        this.paymentId = paymentId;
        this.amount = amount;
        this.paidAt = paidAt;
    }
}
