package com.luna.aggarly.availability.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class AvailabilityReleasedEvent extends ApplicationEvent {
    private final UUID propertyId;
    private final UUID bookingId;

    public AvailabilityReleasedEvent(Object source, UUID propertyId, UUID bookingId) {
        super(source);
        this.propertyId = propertyId;
        this.bookingId = bookingId;
    }
}
