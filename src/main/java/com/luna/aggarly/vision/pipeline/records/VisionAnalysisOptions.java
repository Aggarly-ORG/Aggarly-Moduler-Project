package com.luna.aggarly.vision.pipeline.records;

public record VisionAnalysisOptions(
        boolean runDetailedObjectDetection,
        boolean runOcr,
        boolean runModeration,
        boolean skipCache
) {
    public static VisionAnalysisOptions defaultOptions() {
        return new VisionAnalysisOptions(true, true, true, false);
    }
}
