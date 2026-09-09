package com.luna.aggarly.availability.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MultiSanctuaryCalendarResponse(
        LocalDate from,
        LocalDate to,
        List<SanctuaryCalendarGrid> properties
) {
    public record SanctuaryCalendarGrid(
            UUID propertyId,
            String title,
            List<DailyCalendarCell> days
    ) {}

    public record DailyCalendarCell(
            LocalDate date,
            boolean available,
            BigDecimal price,
            int moonIlluminationPercentage,
            String moonPhaseName,
            boolean lumenYieldActive
    ) {}
}