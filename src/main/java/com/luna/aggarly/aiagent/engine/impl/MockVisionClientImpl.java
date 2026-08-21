package com.luna.aggarly.aiagent.engine.impl;

import com.luna.aggarly.aiagent.engine.VisionClient;
import com.luna.aggarly.aiagent.engine.records.ImageAnalysisResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "aggarly.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockVisionClientImpl implements VisionClient {

    @Override
    public ImageAnalysisResult analyzeImage(String imageKeyOrUrl) {
        log.info("MockVisionClient analyzing image: {}", imageKeyOrUrl);
        return new ImageAnalysisResult(
                "Modern sunlit living room featuring floor-to-ceiling panoramic windows, hardwood oak floor, contemporary leather sofa, and minimalist decor.",
                "Sunlit modern living room with large windows and hardwood floor",
                "Aggarly Verified Listing",
                List.of("sofa", "coffee_table", "panoramic_window", "hardwood_floor", "pendant_light"),
                List.of("modern", "minimalist", "scandinavian", "bright", "spacious"),
                "APPROVED",
                null
        );
    }
}
