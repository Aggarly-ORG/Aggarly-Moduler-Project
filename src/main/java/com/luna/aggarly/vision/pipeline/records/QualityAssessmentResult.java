package com.luna.aggarly.vision.pipeline.records;

import com.luna.aggarly.vision.entity.enums.ImageQualityGrade;

public record QualityAssessmentResult(
        double qualityScore,
        double technicalQualityScore,
        double visualUsabilityScore,
        double searchabilityScore,
        double sharpnessScore,
        double brightnessScore,
        ImageQualityGrade grade,
        boolean blurry,
        boolean dark,
        boolean overexposed,
        boolean screenshot,
        boolean collage,
        boolean passedQualityGate
) {}
