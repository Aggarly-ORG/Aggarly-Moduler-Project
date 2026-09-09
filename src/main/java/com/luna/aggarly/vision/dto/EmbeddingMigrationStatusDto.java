package com.luna.aggarly.vision.dto;

import java.time.Instant;

public record EmbeddingMigrationStatusDto(
    String jobId,
    String status,
    long totalImages,
    long processedImages,
    long failedImages,
    double percentComplete,
    Instant startedAt,
    Instant completedAt
) {}
