package com.luna.aggarly.vision.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.vision.pipeline.OllamaVisionClient;
import com.luna.aggarly.vision.search.records.SearchMode;
import com.luna.aggarly.vision.search.records.VisionSearchFilters;
import com.luna.aggarly.vision.search.records.VisionSearchQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Intelligent search query parser powered by LLM structured extraction,
 * converting natural language queries into strict relational filters and pure visual embedding prompts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VisionQueryParser {

    private final OllamaVisionClient ollamaVisionClient;
    private final ObjectMapper objectMapper;

    public static final String QUERY_INTENT_EXTRACTION_PROMPT = """
            You are a strict, expert real estate and vacation rental search intent parser.
            Given the user's natural language search query, extract all structured filters and isolate the visual aesthetic prompt into a strict JSON object:

            {
              "city": "Santorini",
              "country": "Greece",
              "maxPrice": 450.0,
              "minGuests": 4,
              "propertyType": "VILLA",
              "requiredScenes": ["POOL", "VIEW", "BALCONY"],
              "requiredAmenities": ["private_pool", "sea_view", "wifi"],
              "cleanedVisualPrompt": "luxury cliffside villa with infinity pool and panoramic sunset sea view"
            }

            EXTRACTION RULES:
            1. 'city': Specific city, island, or town (e.g. "Santorini", "Paris", "Rome", "Bali", "Mykonos", "Zermatt", "Nice", "Barcelona", "London", "Tokyo", "Cairo"). Null if not mentioned.
            2. 'country': Country name (e.g. "Greece", "Italy", "Switzerland", "France", "Spain", "Indonesia", "Egypt", "United States"). Null if not mentioned.
            3. 'maxPrice': Numeric maximum price per night (e.g. "under $500", "below 300", "max $400" -> 500.0, 300.0, 400.0). Null if not specified.
            4. 'minGuests': Minimum guest capacity (e.g. "for 4 guests", "family of 5", "2 people" -> 4, 5, 2). Null if not specified.
            5. 'propertyType': "VILLA", "APARTMENT", "HOUSE", "CHALET", "STUDIO", "COTTAGE", "PENTHOUSE", or null.
            6. 'requiredScenes': List of standard scene types matching the intent from: ["BEDROOM", "BATHROOM", "KITCHEN", "LIVING_ROOM", "BALCONY", "POOL", "EXTERIOR", "VIEW", "WORKSPACE"].
            7. 'requiredAmenities': Specific visible amenities (e.g. ["private_pool", "sea_view", "jacuzzi", "fireplace", "wifi", "workspace"]).
            8. 'cleanedVisualPrompt': The user's query stripped of price, guests, and dates, leaving only the rich visual/aesthetic description for vector embedding.
            9. Output valid JSON ONLY. No markdown wrapper, no conversational preamble.
            """;

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
        List<String> requiredAmenities = explicitFilters != null && explicitFilters.requiredAmenities() != null
                ? new ArrayList<>(explicitFilters.requiredAmenities())
                : new ArrayList<>();

        if (hasText) {
            // Use LLM Structured JSON Extraction
            ExtractedQueryIntent intent = extractIntentWithLlm(cleanedText);
            if (intent != null) {
                if (city == null && intent.city != null) city = intent.city;
                if (country == null && intent.country != null) country = intent.country;
                if (maxPrice == null && intent.maxPrice != null) maxPrice = intent.maxPrice;
                if (minGuests == null && intent.minGuests != null) minGuests = intent.minGuests;
                if (intent.requiredScenes != null && !intent.requiredScenes.isEmpty()) {
                    for (String s : intent.requiredScenes) {
                        if (!requiredScenes.contains(s)) requiredScenes.add(s);
                    }
                }
                if (intent.requiredAmenities != null && !intent.requiredAmenities.isEmpty()) {
                    for (String a : intent.requiredAmenities) {
                        if (!requiredAmenities.contains(a)) requiredAmenities.add(a);
                    }
                }
                if (intent.cleanedVisualPrompt != null && !intent.cleanedVisualPrompt.isBlank()) {
                    cleanedText = intent.cleanedVisualPrompt;
                }
            }
        }

        VisionSearchFilters effectiveFilters = new VisionSearchFilters(
                city,
                country,
                explicitFilters != null ? explicitFilters.checkInDate() : null,
                explicitFilters != null ? explicitFilters.checkOutDate() : null,
                minGuests,
                maxPrice,
                requiredAmenities.isEmpty() ? null : requiredAmenities,
                requiredScenes.isEmpty() ? null : requiredScenes,
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

    private ExtractedQueryIntent extractIntentWithLlm(String rawText) {
        if (ollamaVisionClient == null || !ollamaVisionClient.isAvailable()) {
            return null;
        }

        try {
            var responseOpt = ollamaVisionClient.chatWithVision(
                    null,
                    QUERY_INTENT_EXTRACTION_PROMPT,
                    "Parse this search query: \"" + rawText + "\"",
                    null
            );

            if (responseOpt.isPresent()) {
                String raw = responseOpt.get();
                int first = raw.indexOf('{');
                int last = raw.lastIndexOf('}');
                if (first >= 0 && last > first) {
                    JsonNode root = objectMapper.readTree(raw.substring(first, last + 1));
                    ExtractedQueryIntent intent = new ExtractedQueryIntent();
                    intent.city = root.hasNonNull("city") ? root.get("city").asText() : null;
                    intent.country = root.hasNonNull("country") ? root.get("country").asText() : null;
                    intent.maxPrice = root.hasNonNull("maxPrice") ? root.get("maxPrice").asDouble() : null;
                    intent.minGuests = root.hasNonNull("minGuests") ? root.get("minGuests").asInt() : null;
                    intent.propertyType = root.hasNonNull("propertyType") ? root.get("propertyType").asText() : null;
                    intent.cleanedVisualPrompt = root.hasNonNull("cleanedVisualPrompt") ? root.get("cleanedVisualPrompt").asText() : null;

                    if (root.has("requiredScenes") && root.get("requiredScenes").isArray()) {
                        intent.requiredScenes = new ArrayList<>();
                        for (JsonNode sn : root.get("requiredScenes")) {
                            intent.requiredScenes.add(sn.asText().toUpperCase());
                        }
                    }

                    if (root.has("requiredAmenities") && root.get("requiredAmenities").isArray()) {
                        intent.requiredAmenities = new ArrayList<>();
                        for (JsonNode an : root.get("requiredAmenities")) {
                            intent.requiredAmenities.add(an.asText().toLowerCase());
                        }
                    }

                    log.info("LLM query intent extraction: city={}, country={}, maxPrice={}, guests={}, scenes={}, cleanedPrompt='{}'",
                            intent.city, intent.country, intent.maxPrice, intent.minGuests, intent.requiredScenes, intent.cleanedVisualPrompt);
                    return intent;
                }
            }
        } catch (Exception e) {
            log.warn("LLM query intent extraction failed: {}, using raw text", e.getMessage());
        }

        return null;
    }

    private static class ExtractedQueryIntent {
        String city;
        String country;
        Double maxPrice;
        Integer minGuests;
        String propertyType;
        String cleanedVisualPrompt;
        List<String> requiredScenes;
        List<String> requiredAmenities;
    }
}
