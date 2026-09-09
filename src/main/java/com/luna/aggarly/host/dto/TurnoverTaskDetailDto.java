package com.luna.aggarly.host.dto;

import java.util.List;

public record TurnoverTaskDetailDto(
        String id,
        String sanctuaryId,
        String sanctuaryTitle,
        String unitCode,
        String taskType,
        String status,
        String scheduledTime,
        String estimatedDuration,
        List<String> assignedSpecialists,
        Double acousticDbReading,
        Boolean silenceCertified,
        String notes
) {}