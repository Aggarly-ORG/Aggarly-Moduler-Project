package com.luna.aggarly.vision.search.records;

import java.util.List;
import java.util.UUID;

public record VisionSearchResult(
        UUID propertyId,
        String title,
        String city,
        String country,
        Double pricePerNight,
        Integer maxGuests,
        float finalScore,
        float visualSimilarityScore,
        float descriptionMatchScore,
        UUID bestMatchImageId,
        String bestMatchImageUrl,
        String bestMatchSceneType,
        String visualExplanation,
        List<String> matchedFeatures,
        List<String> matchedStyleTags,
        double coverageScore
) {}
