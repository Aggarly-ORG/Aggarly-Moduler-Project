package com.luna.aggarly.vision.search;

import org.springframework.stereotype.Component;

@Component
public class ScoreCalibrator {

    private static final float NOISE_FLOOR = 0.15f;
    private static final float TOP_ANCHOR = 0.80f;

    public float calibrate(float rawScore) {
        if (rawScore <= NOISE_FLOOR) {
            return 0.0f;
        }

        if (rawScore >= TOP_ANCHOR) {
            return Math.min(1.0f, 0.95f + ((rawScore - TOP_ANCHOR) / (1.0f - TOP_ANCHOR)) * 0.05f);
        }

        float normalizedT = (rawScore - NOISE_FLOOR) / (TOP_ANCHOR - NOISE_FLOOR);
        float calibrated = 0.45f + (normalizedT * 0.50f);

        return Math.min(1.0f, Math.max(0.0f, calibrated));
    }
}
