package com.luna.aggarly.vision.evaluation.records;

public record ABComparisonReport(
        double ndcgDelta,
        double recallDelta,
        double latencyDeltaMs,
        int winCount,
        int tieCount,
        int lossCount,
        String recommendation,
        String summaryNotes
) {}
