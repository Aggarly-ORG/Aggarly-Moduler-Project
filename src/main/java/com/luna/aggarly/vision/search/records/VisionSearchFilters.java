package com.luna.aggarly.vision.search.records;

import java.time.LocalDate;
import java.util.List;

public record VisionSearchFilters(
        String city,
        String country,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        Integer minGuests,
        Double maxPricePerNight,
        List<String> requiredAmenities,
        List<String> requiredSceneTypes,
        Double minQualityScore
) {
    public static VisionSearchFilters empty() {
        return new VisionSearchFilters(null, null, null, null, null, null, null, null, null);
    }
}
