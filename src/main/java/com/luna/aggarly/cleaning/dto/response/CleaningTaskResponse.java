package com.luna.aggarly.cleaning.dto.response;

import com.luna.aggarly.cleaning.entity.enums.CleaningPriority;
import com.luna.aggarly.cleaning.entity.enums.CleaningStatus;
import com.luna.aggarly.cleaning.entity.enums.CleaningTaskType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record CleaningTaskResponse(
        UUID id,
        UUID propertyId,
        UUID bookingId,
        UUID hostId,
        UUID assignedCleanerId,
        CleaningStatus status,
        CleaningPriority priority,
        CleaningTaskType taskType,
        LocalDate scheduledDate,
        LocalTime scheduledStartTime,
        int estimatedDurationMinutes,
        Instant actualStartedAt,
        Instant actualCompletedAt,
        String cleanerNotes,
        String hostFeedback,
        Integer ratingByHost,
        List<CleaningChecklistResponse> checklists,
        List<CleaningPhotoResponse> photos,
        List<CleaningIssueResponse> issues,
        Instant createdAt,
        Instant updatedAt
) {}
