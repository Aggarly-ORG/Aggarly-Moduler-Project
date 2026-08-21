package com.luna.aggarly.payment.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class RefundSucceededEvent extends ApplicationEvent {
    private final UUID bookingId;
    private final UUID refundId;
    private final BigDecimal amount;

    public RefundSucceededEvent(Object source, UUID bookingId, UUID refundId, BigDecimal amount) {
        super(source);
        this.bookingId = bookingId;
        this.refundId = refundId;
        this.amount = amount;
    }
}
