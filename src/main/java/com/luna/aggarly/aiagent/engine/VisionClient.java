package com.luna.aggarly.aiagent.engine;

import com.luna.aggarly.aiagent.engine.records.ImageAnalysisResult;

public interface VisionClient {
    ImageAnalysisResult analyzeImage(String imageKeyOrUrl);
}
