package com.luna.aggarly.cleaning.event;

import com.luna.aggarly.cleaning.entity.enums.IssueSeverity;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class CleaningIssueReportedEvent extends ApplicationEvent {

    private final UUID issueId;
    private final UUID taskId;
    private final UUID propertyId;
    private final UUID bookingId;
    private final UUID hostId;
    private final UUID reportedBy;
    private final String title;
    private final IssueSeverity severity;

    public CleaningIssueReportedEvent(Object source,
                                      UUID issueId,
                                      UUID taskId,
                                      UUID propertyId,
                                      UUID bookingId,
                                      UUID hostId,
                                      UUID reportedBy,
                                      String title,
                                      IssueSeverity severity) {
        super(source);
        this.issueId = issueId;
        this.taskId = taskId;
        this.propertyId = propertyId;
        this.bookingId = bookingId;
        this.hostId = hostId;
        this.reportedBy = reportedBy;
        this.title = title;
        this.severity = severity;
    }
}
