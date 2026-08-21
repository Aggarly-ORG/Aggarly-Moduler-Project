package com.luna.aggarly.vision.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PropertyVisualProfileDto(
        UUID propertyId,
        String profileStatus,
        int totalImages,
        int processedImages,
        int usableImages,
        Double coverageScore,
        Map<String, Integer> roomCoverage,
        Map<String, String> bestPerScene,
        List<String> missingKeyRooms,
        UUID recommendedCoverImageId,
        List<String> representativeImageIds,
        Double romanticScore,
        Double luxuryScore,
        Double familyScore,
        Double businessScore,
        Double relaxationScore,
        List<String> aggregatedStyleTags,
        List<String> aggregatedAmenities,
        String visualSummary
) {}
