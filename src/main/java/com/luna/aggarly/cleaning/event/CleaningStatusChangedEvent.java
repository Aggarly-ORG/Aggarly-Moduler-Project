package com.luna.aggarly.cleaning.event;

import com.luna.aggarly.cleaning.entity.enums.CleaningStatus;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class CleaningStatusChangedEvent extends ApplicationEvent {

    private final UUID taskId;
    private final UUID propertyId;
    private final UUID hostId;
    private final UUID cleanerId;
    private final CleaningStatus previousStatus;
    private final CleaningStatus newStatus;

    public CleaningStatusChangedEvent(Object source,
                                      UUID taskId,
                                      UUID propertyId,
                                      UUID hostId,
                                      UUID cleanerId,
                                      CleaningStatus previousStatus,
                                      CleaningStatus newStatus) {
        super(source);
        this.taskId = taskId;
        this.propertyId = propertyId;
        this.hostId = hostId;
        this.cleanerId = cleanerId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
    }
}
