package com.luna.aggarly.vision.evaluation.records;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record QueryEvalResult(
        UUID queryId,
        String queryText,
        double ndcgAt5,
        double ndcgAt10,
        double recallAt10,
        double precisionAt5,
        double reciprocalRank,
        boolean filterCorrect,
        String topChannel,
        List<StageLatency> latencies,
        List<UUID> retrievedPropertyIds
) {}
