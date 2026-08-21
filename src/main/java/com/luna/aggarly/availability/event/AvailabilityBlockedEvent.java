package com.luna.aggarly.availability.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDate;
import java.util.UUID;

@Getter
public class AvailabilityBlockedEvent extends ApplicationEvent {
    private final UUID propertyId;
    private final UUID bookingId;
    private final LocalDate checkIn;
    private final LocalDate checkOut;

    public AvailabilityBlockedEvent(Object source, UUID propertyId, UUID bookingId,
                                     LocalDate checkIn, LocalDate checkOut) {
        super(source);
        this.propertyId = propertyId;
        this.bookingId = bookingId;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
    }
}
