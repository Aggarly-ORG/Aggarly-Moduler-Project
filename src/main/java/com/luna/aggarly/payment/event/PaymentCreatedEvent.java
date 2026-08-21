package com.luna.aggarly.payment.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class PaymentCreatedEvent extends ApplicationEvent {
    private final UUID bookingId;
    private final UUID paymentId;
    private final BigDecimal amount;
    private final String currency;

    public PaymentCreatedEvent(Object source, UUID bookingId, UUID paymentId, BigDecimal amount, String currency) {
        super(source);
        this.bookingId = bookingId;
        this.paymentId = paymentId;
        this.amount = amount;
        this.currency = currency;
    }
}
