package com.luna.aggarly.vision.dto;

import com.luna.aggarly.vision.search.records.VisionSearchFilters;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record VisionMultimodalSearchRequest(
        @NotBlank(message = "Reference object key is required")
        String referenceObjectKey,
        String textQuery,
        Integer pageSize,
        String city,
        String country,
        Integer minGuests,
        Double maxPricePerNight,
        List<String> requiredSceneTypes
) {
    public VisionSearchFilters toFilters() {
        return new VisionSearchFilters(city, country, null, null, minGuests, maxPricePerNight, null, requiredSceneTypes, null);
    }
}
