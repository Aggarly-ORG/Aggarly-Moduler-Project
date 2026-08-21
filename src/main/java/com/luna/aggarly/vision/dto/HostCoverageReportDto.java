package com.luna.aggarly.vision.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record HostCoverageReportDto(
        UUID propertyId,
        double coverageScore,
        Map<String, Integer> roomCoverage,
        List<String> missingKeyRooms,
        List<String> recommendations
) {}
