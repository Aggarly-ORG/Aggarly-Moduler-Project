package com.luna.aggarly.vision.dto;

import java.time.Instant;

public record GroundTruthSeederStatusDto(
    String status,
    int seededProperties,
    int seededPhotos,
    int seededQueries,
    Instant lastRunAt,
    String message
) {}
