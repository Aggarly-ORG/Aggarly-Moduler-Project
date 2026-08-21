package com.luna.aggarly.vision.evaluation;

import com.luna.aggarly.vision.evaluation.records.EvaluationConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VisionEvaluationScheduler {

    private final VisionSearchEvaluator searchEvaluator;

    @Scheduled(cron = "${aggarly.vision.evaluation.cron:0 0 2 * * SUN}")
    public void runWeeklyEvaluation() {
        log.info("Running scheduled weekly vision retrieval evaluation");
        try {
            searchEvaluator.evaluate(EvaluationConfig.defaultConfig());
        } catch (Exception e) {
            log.error("Scheduled weekly vision evaluation failed: {}", e.getMessage(), e);
        }
    }
}
