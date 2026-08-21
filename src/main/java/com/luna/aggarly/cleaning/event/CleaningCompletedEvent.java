package com.luna.aggarly.cleaning.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class CleaningCompletedEvent extends ApplicationEvent {

    private final UUID taskId;
    private final UUID propertyId;
    private final UUID bookingId;
    private final UUID hostId;
    private final UUID cleanerId;

    public CleaningCompletedEvent(Object source,
                                  UUID taskId,
                                  UUID propertyId,
                                  UUID bookingId,
                                  UUID hostId,
                                  UUID cleanerId) {
        super(source);
        this.taskId = taskId;
        this.propertyId = propertyId;
        this.bookingId = bookingId;
        this.hostId = hostId;
        this.cleanerId = cleanerId;
    }
}
