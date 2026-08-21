package com.luna.aggarly.scheduler.dto;

import com.luna.aggarly.scheduler.entity.enums.TaskStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Builder
public record TaskExecutionResponse(
        UUID id,
        UUID taskId,
        Integer planVersion,
        TaskStatus status,
        Integer attempt,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        String errorCode,
        String errorMessage,
        Map<String, Object> stepResults,
        String correlationId
) {}
