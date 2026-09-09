package com.luna.aggarly.scheduler.dto;

public record ScheduledTaskMetricsResponse(
        String clusterHealth,
        long activeCronJobsCount,
        double sevenDaySuccessRatePercent,
        int workerPoolConcurrency,
        long totalExecutions
) {}