package com.luna.aggarly.booking.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class BookingCancelledEvent extends ApplicationEvent {

    private final UUID bookingId;
    private final BigDecimal refundAmount;
    private final String reason;

    public BookingCancelledEvent(Object source, UUID bookingId, BigDecimal refundAmount, String reason) {
        super(source);
        this.bookingId = bookingId;
        this.refundAmount = refundAmount;
        this.reason = reason;
    }
}
