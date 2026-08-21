package com.luna.aggarly.vision.evaluation.records;

public record EvaluationConfig(
        int topK,
        boolean includeLlmReranker,
        boolean evaluateStages,
        int sampleLimit
) {
    public static EvaluationConfig defaultConfig() {
        return new EvaluationConfig(10, true, true, 50);
    }
}
