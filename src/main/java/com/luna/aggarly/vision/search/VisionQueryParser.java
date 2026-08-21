package com.luna.aggarly.vision.search;

import com.luna.aggarly.vision.search.records.SearchMode;
import com.luna.aggarly.vision.search.records.VisionSearchFilters;
import com.luna.aggarly.vision.search.records.VisionSearchQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class VisionQueryParser {

    private static final Pattern PRICE_PATTERN = Pattern.compile("(?i)(?:under|less than|max|below|up to)\\s*\\$?(\\d+)");
    private static final Pattern GUESTS_PATTERN = Pattern.compile("(?i)(\\d+)\\s*(?:guests|people|persons|adults)");

    public VisionSearchQuery parse(String rawText, byte[] referenceImageBytes, String referenceObjectKey, VisionSearchFilters explicitFilters, int pageSize, String cursor) {
        SearchMode mode;
        boolean hasImage = (referenceImageBytes != null && referenceImageBytes.length > 0) || (referenceObjectKey != null && !referenceObjectKey.isBlank());
        boolean hasText = rawText != null && !rawText.trim().isEmpty();

        if (hasImage && hasText) {
            mode = SearchMode.MULTIMODAL;
        } else if (hasImage) {
            mode = SearchMode.IMAGE_ONLY;
        } else {
            mode = SearchMode.TEXT_ONLY;
        }

        String cleanedText = rawText != null ? rawText.trim() : "";
        Double maxPrice = explicitFilters != null ? explicitFilters.maxPricePerNight() : null;
        Integer minGuests = explicitFilters != null ? explicitFilters.minGuests() : null;
        String city = explicitFilters != null ? explicitFilters.city() : null;
        String country = explicitFilters != null ? explicitFilters.country() : null;
        List<String> requiredScenes = explicitFilters != null && explicitFilters.requiredSceneTypes() != null
                ? new ArrayList<>(explicitFilters.requiredSceneTypes())
                : new ArrayList<>();

        if (hasText) {
            // Extract inline price constraint if not explicitly provided
            if (maxPrice == null) {
                Matcher priceMatcher = PRICE_PATTERN.matcher(cleanedText);
                if (priceMatcher.find()) {
                    maxPrice = Double.parseDouble(priceMatcher.group(1));
                }
            }

            // Extract inline guest count if not explicitly provided
            if (minGuests == null) {
                Matcher guestMatcher = GUESTS_PATTERN.matcher(cleanedText);
                if (guestMatcher.find()) {
                    minGuests = Integer.parseInt(guestMatcher.group(1));
                }
            }

            // Extract inline scene types
            String lower = cleanedText.toLowerCase();
            if (lower.contains("pool") || lower.contains("swimming")) requiredScenes.add("POOL");
            if (lower.contains("sea view") || lower.contains("ocean view") || lower.contains("city view")) requiredScenes.add("VIEW");
            if (lower.contains("balcony") || lower.contains("terrace")) requiredScenes.add("BALCONY");
            if (lower.contains("workspace") || lower.contains("office") || lower.contains("desk")) requiredScenes.add("WORKSPACE");
        }

        VisionSearchFilters effectiveFilters = new VisionSearchFilters(
                city,
                country,
                explicitFilters != null ? explicitFilters.checkInDate() : null,
                explicitFilters != null ? explicitFilters.checkOutDate() : null,
                minGuests,
                maxPrice,
                explicitFilters != null ? explicitFilters.requiredAmenities() : null,
                requiredScenes,
                explicitFilters != null ? explicitFilters.minQualityScore() : null
        );

        float imageWeight = switch (mode) {
            case TEXT_ONLY -> 0.0f;
            case IMAGE_ONLY -> 1.0f;
            case MULTIMODAL -> 0.60f;
        };

        return new VisionSearchQuery(
                cleanedText,
                referenceImageBytes,
                referenceObjectKey,
                imageWeight,
                mode,
                pageSize > 0 ? pageSize : 10,
                cursor,
                0.30f,
                effectiveFilters
        );
    }
}
