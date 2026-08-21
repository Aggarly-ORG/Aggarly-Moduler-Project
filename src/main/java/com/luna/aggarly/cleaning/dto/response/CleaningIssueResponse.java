package com.luna.aggarly.cleaning.dto.response;

import com.luna.aggarly.cleaning.entity.enums.IssueSeverity;

import java.time.Instant;
import java.util.UUID;

public record CleaningIssueResponse(
        UUID id,
        UUID cleaningTaskId,
        UUID propertyId,
        UUID bookingId,
        UUID reportedBy,
        String title,
        String description,
        IssueSeverity severity,
        String photoKeys,
        boolean resolved,
        String resolutionNotes,
        Instant resolvedAt,
        Instant createdAt
) {}
