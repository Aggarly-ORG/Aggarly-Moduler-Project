package com.luna.aggarly.cleaning.event;

import com.luna.aggarly.cleaning.entity.enums.CleaningPriority;
import com.luna.aggarly.cleaning.entity.enums.CleaningTaskType;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDate;
import java.util.UUID;

@Getter
public class CleaningTaskCreatedEvent extends ApplicationEvent {

    private final UUID taskId;
    private final UUID propertyId;
    private final UUID bookingId;
    private final UUID hostId;
    private final UUID assignedCleanerId;
    private final CleaningTaskType taskType;
    private final CleaningPriority priority;
    private final LocalDate scheduledDate;

    public CleaningTaskCreatedEvent(Object source,
                                    UUID taskId,
                                    UUID propertyId,
                                    UUID bookingId,
                                    UUID hostId,
                                    UUID assignedCleanerId,
                                    CleaningTaskType taskType,
                                    CleaningPriority priority,
                                    LocalDate scheduledDate) {
        super(source);
        this.taskId = taskId;
        this.propertyId = propertyId;
        this.bookingId = bookingId;
        this.hostId = hostId;
        this.assignedCleanerId = assignedCleanerId;
        this.taskType = taskType;
        this.priority = priority;
        this.scheduledDate = scheduledDate;
    }
}
