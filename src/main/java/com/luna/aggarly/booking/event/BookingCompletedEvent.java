package com.luna.aggarly.booking.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class BookingCompletedEvent extends ApplicationEvent {

    private final UUID bookingId;

    public BookingCompletedEvent(Object source, UUID bookingId) {
        super(source);
        this.bookingId = bookingId;
    }
}
