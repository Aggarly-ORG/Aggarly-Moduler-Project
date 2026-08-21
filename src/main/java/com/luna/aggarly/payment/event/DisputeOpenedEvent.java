package com.luna.aggarly.payment.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class DisputeOpenedEvent extends ApplicationEvent {
    private final UUID bookingId;
    private final UUID paymentId;
    private final BigDecimal disputeAmount;

    public DisputeOpenedEvent(Object source, UUID bookingId, UUID paymentId, BigDecimal disputeAmount) {
        super(source);
        this.bookingId = bookingId;
        this.paymentId = paymentId;
        this.disputeAmount = disputeAmount;
    }
}
