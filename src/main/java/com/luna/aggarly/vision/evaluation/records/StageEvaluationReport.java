package com.luna.aggarly.vision.evaluation.records;

import java.util.Map;

public record StageEvaluationReport(
        double imageChannelNdcg,
        double captionChannelNdcg,
        double descriptionChannelNdcg,
        double fusedNdcg,
        double aggregatedNdcg,
        double mlRerankedNdcg,
        double llmRerankedNdcg,
        Map<String, Long> stageAverageLatenciesMs
) {}
