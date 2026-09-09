package com.luna.aggarly.vision.dto;

public record ClusterHardwareTelemetry(
    String gpuModel,
    long vramAllocatedBytes,
    long vramTotalBytes,
    double gpuUtilizationPercent,
    int temperatureCelsius,
    int activeInferenceStreams
) {}
