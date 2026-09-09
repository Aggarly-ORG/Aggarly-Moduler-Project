package com.luna.aggarly.common.util;

import java.time.LocalDate;

public final class CelestialEphemerisCalculator {

    private static final double SYNODIC_MONTH = 29.53058867;
    private static final long REF_EPOCH_DAY = 19733; // 2024-01-11 New Moon

    private CelestialEphemerisCalculator() {}

    public static int calculateMoonIlluminationPercent(LocalDate date) {
        long diff = date.toEpochDay() - REF_EPOCH_DAY;
        double daysIntoCycle = diff % SYNODIC_MONTH;
        if (daysIntoCycle < 0) {
            daysIntoCycle += SYNODIC_MONTH;
        }
        double phaseAngle = (daysIntoCycle / SYNODIC_MONTH) * 2.0 * Math.PI;
        double illumination = (1.0 - Math.cos(phaseAngle)) / 2.0;
        return (int) Math.round(illumination * 100.0);
    }

    public static String calculateMoonPhaseName(LocalDate date) {
        long diff = date.toEpochDay() - REF_EPOCH_DAY;
        double daysIntoCycle = diff % SYNODIC_MONTH;
        if (daysIntoCycle < 0) {
            daysIntoCycle += SYNODIC_MONTH;
        }
        double phaseAngle = (daysIntoCycle / SYNODIC_MONTH) * 2.0 * Math.PI;
        int illumination = (int) Math.round(((1.0 - Math.cos(phaseAngle)) / 2.0) * 100.0);

        if (illumination <= 2) return "NEW_MOON";
        if (illumination >= 98) return "FULL_MOON";

        if (phaseAngle < Math.PI) {
            if (illumination < 48) return "WAXING_CRESCENT";
            if (illumination <= 52) return "FIRST_QUARTER";
            return "WAXING_GIBBOUS";
        } else {
            if (illumination > 52) return "WANING_GIBBOUS";
            if (illumination >= 48) return "LAST_QUARTER";
            return "WANING_CRESCENT";
        }
    }
}