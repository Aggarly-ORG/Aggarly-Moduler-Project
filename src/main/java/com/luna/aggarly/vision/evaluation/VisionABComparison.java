package com.luna.aggarly.vision.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.vision.entity.VisionEvaluationRun;
import com.luna.aggarly.vision.evaluation.records.ABComparisonReport;
import com.luna.aggarly.vision.evaluation.records.EvaluationReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VisionABComparison {

    private final ObjectMapper objectMapper;

    public ABComparisonReport compareRuns(VisionEvaluationRun baseline, VisionEvaluationRun candidate, String changeDescription) {
        log.info("Comparing baseline run {} against candidate run {}", baseline.getId(), candidate.getId());

        double ndcgDelta = candidate.getNdcgAt10() - baseline.getNdcgAt10();
        double recallDelta = candidate.getRecallAt10() - baseline.getRecallAt10();
        double latencyDeltaMs = 0.0;

        int winCount = 0, tieCount = 0, lossCount = 0;
        if (ndcgDelta > 0.005) {
            winCount = 1;
        } else if (ndcgDelta < -0.005) {
            lossCount = 1;
        } else {
            tieCount = 1;
        }

        String recommendation;
        if (candidate.getFilterCorrectness() < 1.0) {
            recommendation = "ROLLBACK: Hard filter correctness violated!";
        } else if (ndcgDelta > 0.02 && recallDelta >= 0.0) {
            recommendation = "PROMOTE: Significant NDCG@10 gain achieved.";
        } else if (ndcgDelta >= -0.005) {
            recommendation = "NEUTRAL: Comparable quality performance.";
        } else {
            recommendation = "ROLLBACK: Quality regression detected.";
        }

        String summary = String.format("A/B comparison for '%s': NDCG Delta = %+.4f, Recall Delta = %+.4f. Decision: %s",
                changeDescription, ndcgDelta, recallDelta, recommendation);

        return new ABComparisonReport(
                ndcgDelta,
                recallDelta,
                latencyDeltaMs,
                winCount,
                tieCount,
                lossCount,
                recommendation,
                summary
        );
    }
}
