package com.luna.aggarly.vision.evaluation.records;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record EvaluationReport(
        String pipelineVersion,
        Instant evaluatedAt,
        int totalQueriesEvaluated,
        double meanNdcgAt5,
        double meanNdcgAt10,
        double meanRecallAt10,
        double meanPrecisionAt5,
        double meanReciprocalRank,
        double filterCorrectness,
        Map<String, Double> channelAttribution,
        StageEvaluationReport stageReport,
        List<QueryEvalResult> queryResults
) {}
