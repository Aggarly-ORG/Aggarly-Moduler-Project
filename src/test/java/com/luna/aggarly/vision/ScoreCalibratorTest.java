package com.luna.aggarly.vision;

import com.luna.aggarly.vision.search.ScoreCalibrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreCalibratorTest {

    private ScoreCalibrator calibrator;

    @BeforeEach
    void setUp() {
        calibrator = new ScoreCalibrator();
    }

    @Test
    @DisplayName("Verify noise floor maps to 0.00 (0%)")
    void testNoiseFloor() {
        assertEquals(0.0f, calibrator.calibrate(0.02f), "Out-of-domain / random noise must be 0.00");
        assertEquals(0.0f, calibrator.calibrate(0.14f), "Sub-noise floor must be 0.00");
    }

    @Test
    @DisplayName("Verify strong semantic matches (0.35 - 0.45) map to intuitive 65% - 75% confidence")
    void testSemanticMatchCalibration() {
        float calibrated = calibrator.calibrate(0.389f);
        System.out.println("Calibrated 0.389 -> " + calibrated);
        assertTrue(calibrated >= 0.60f && calibrated <= 0.75f, "0.389 raw cosine should calibrate to ~65%-70% confidence");
    }

    @Test
    @DisplayName("Verify near identical matches (>= 0.85) map to 95% - 100% confidence")
    void testTopMatchCalibration() {
        float calibrated = calibrator.calibrate(0.85f);
        System.out.println("Calibrated 0.85 -> " + calibrated);
        assertTrue(calibrated >= 0.95f, "0.85 raw cosine should calibrate to >= 95% confidence");

        float perfect = calibrator.calibrate(1.00f);
        assertEquals(1.00f, perfect, 0.001f, "1.00 raw cosine should calibrate to 100%");
    }
}
