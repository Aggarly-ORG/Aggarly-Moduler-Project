package com.luna.aggarly.payment.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class RefundCreatedEvent extends ApplicationEvent {
    private final UUID bookingId;
    private final UUID paymentId;
    private final UUID refundId;
    private final BigDecimal amount;
    private final String reason;

    public RefundCreatedEvent(Object source, UUID bookingId, UUID paymentId, UUID refundId, BigDecimal amount, String reason) {
        super(source);
        this.bookingId = bookingId;
        this.paymentId = paymentId;
        this.refundId = refundId;
        this.amount = amount;
        this.reason = reason;
    }
}
