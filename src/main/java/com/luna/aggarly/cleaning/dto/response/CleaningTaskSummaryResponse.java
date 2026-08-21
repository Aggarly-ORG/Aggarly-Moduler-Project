package com.luna.aggarly.cleaning.dto.response;

import com.luna.aggarly.cleaning.entity.enums.CleaningPriority;
import com.luna.aggarly.cleaning.entity.enums.CleaningStatus;
import com.luna.aggarly.cleaning.entity.enums.CleaningTaskType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CleaningTaskSummaryResponse(
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
        Integer ratingByHost,
        int checklistRoomsCount,
        int photosCount,
        int issuesCount
) {}
