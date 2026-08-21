package com.luna.aggarly.booking.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class BookingConfirmedEvent extends ApplicationEvent {

    private final UUID bookingId;

    public BookingConfirmedEvent(Object source, UUID bookingId) {
        super(source);
        this.bookingId = bookingId;
    }
}
