package com.luna.aggarly.vision.evaluation.records;

public record StageLatency(
        String stageName,
        long durationMs
) {}
