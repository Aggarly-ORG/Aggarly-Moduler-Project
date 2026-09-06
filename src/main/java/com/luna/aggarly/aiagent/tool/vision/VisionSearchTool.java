package com.luna.aggarly.aiagent.tool.vision;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import com.luna.aggarly.vision.entity.PropertyVisualProfile;
import com.luna.aggarly.vision.repository.PropertyVisualProfileRepository;
import com.luna.aggarly.vision.search.ImageSimilaritySearchService;
import com.luna.aggarly.vision.search.VisionSearchOrchestrator;
import com.luna.aggarly.vision.search.records.VisionSearchFilters;
import com.luna.aggarly.vision.search.records.VisionSearchQuery;
import com.luna.aggarly.vision.search.records.VisionSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class VisionSearchTool implements Tool<VisionSearchTool.Params, List<VisionSearchResult>> {

    private static final Set<String> MODES = Set.of("TEXT", "IMAGE", "MULTIMODAL", "SIMILAR_PROPERTY");

    private final VisionSearchOrchestrator searchOrchestrator;
    private final ImageSimilaritySearchService imageSearchService;
    private final PropertyVisualProfileRepository profileRepository;
    private final JsonSchemaService jsonSchemaService;

    public record Params(
            @JsonPropertyDescription("Search mode: TEXT (aesthetic/style description), IMAGE (visual match to an uploaded image), " +
                    "MULTIMODAL (image + text refinement) or SIMILAR_PROPERTY (match a liked property). Defaults to TEXT.")
            String mode,

            @JsonPropertyDescription("Text description of desired aesthetics, style or features. Required for TEXT; optional refinement for MULTIMODAL.")
            String query,

            @JsonPropertyDescription("Storage object key of the uploaded reference image. Required for IMAGE and MULTIMODAL.")
            String referenceObjectKey,

            @JsonPropertyDescription("UUID of the liked property to match visually. Required for SIMILAR_PROPERTY.")
            UUID referencePropertyId,

            @JsonPropertyDescription("Maximum number of results to return (default 10).")
            Integer pageSize,

            @JsonPropertyDescription("Opaque pagination cursor from a previous result page.")
            String cursor,

            @JsonPropertyDescription("Optional city filter.")
            String city,

            @JsonPropertyDescription("Optional country filter.")
            String country,

            @JsonPropertyDescription("Optional minimum guest capacity filter.")
            Integer minGuests,

            @JsonPropertyDescription("Optional maximum nightly price filter.")
            Double maxPricePerNight,

            @JsonPropertyDescription("Optional scene types to require, e.g. [\"POOL\", \"SEA_VIEW\"].")
            List<String> requiredSceneTypes
    ) {}

    @Override
    public String name() {
        return "vision.search";
    }

    @Override
    public String description() {
        return "Multimodal visual property search. Modes: TEXT (semantic aesthetic search), IMAGE (find properties similar " +
                "to an uploaded photo), MULTIMODAL (photo + text constraints combined) and SIMILAR_PROPERTY (stylistically " +
                "similar to an existing liked property).";
    }

    @Override
    public Class<Params> parameterType() {
        return Params.class;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ToolResult<List<VisionSearchResult>> execute(Params params, UserPrincipal currentUser) {
        String mode = normalizeMode(params != null ? params.mode() : null);
        if (!MODES.contains(mode)) {
            return ToolResult.failed("INVALID_SEARCH_MODE",
                    "Unknown mode '" + (params != null ? params.mode() : "") + "'. Use TEXT, IMAGE, MULTIMODAL or SIMILAR_PROPERTY.");
        }
        try {
            return switch (mode) {
                case "TEXT" -> searchByText(params);
                case "IMAGE" -> searchByImage(params);
                case "MULTIMODAL" -> searchMultimodal(params);
                case "SIMILAR_PROPERTY" -> searchSimilarToProperty(params);
                default -> ToolResult.failed("INVALID_SEARCH_MODE", "Unsupported mode.");
            };
        } catch (Exception ex) {
            log.error("vision.search failed (mode={})", mode, ex);
            return ToolResult.failed("VISION_SEARCH_FAILED", ex.getMessage());
        }
    }

    private String normalizeMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return "TEXT";
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private ToolResult<List<VisionSearchResult>> searchByText(Params params) {
        if (params == null || params.query() == null || params.query().isBlank()) {
            return ToolResult.failed("MISSING_QUERY", "TEXT mode requires a 'query' describing the desired aesthetics.");
        }
        VisionSearchQuery query = VisionSearchQuery.textQuery(
                params.query(),
                buildFilters(params, true),
                pageSizeOrDefault(params),
                params.cursor()
        );
        return ToolResult.success(searchOrchestrator.search(query));
    }

    private ToolResult<List<VisionSearchResult>> searchByImage(Params params) {
        if (params == null || params.referenceObjectKey() == null || params.referenceObjectKey().isBlank()) {
            return ToolResult.failed("MISSING_REFERENCE_IMAGE", "IMAGE mode requires 'referenceObjectKey'.");
        }
        List<VisionSearchResult> results = imageSearchService.findSimilarByImageKey(
                params.referenceObjectKey(),
                null,
                buildFilters(params, false),
                pageSizeOrDefault(params)
        );
        return ToolResult.success(results);
    }

    private ToolResult<List<VisionSearchResult>> searchMultimodal(Params params) {
        if (params == null || params.referenceObjectKey() == null || params.referenceObjectKey().isBlank()) {
            return ToolResult.failed("MISSING_REFERENCE_IMAGE", "MULTIMODAL mode requires 'referenceObjectKey'.");
        }
        List<VisionSearchResult> results = imageSearchService.findSimilarByImageKey(
                params.referenceObjectKey(),
                params.query(),
                buildFilters(params, false),
                pageSizeOrDefault(params)
        );
        return ToolResult.success(results);
    }

    private ToolResult<List<VisionSearchResult>> searchSimilarToProperty(Params params) {
        if (params == null || params.referencePropertyId() == null) {
            return ToolResult.failed("MISSING_REFERENCE_PROPERTY", "SIMILAR_PROPERTY mode requires 'referencePropertyId'.");
        }
        PropertyVisualProfile profile = profileRepository.findByPropertyId(params.referencePropertyId()).orElse(null);
        if (profile == null) {
            return ToolResult.failure("Visual profile not found for source property: " + params.referencePropertyId());
        }

        String searchSummary = profile.getVisualSummary() != null ? profile.getVisualSummary() : "Luxury contemporary property";
        VisionSearchQuery query = VisionSearchQuery.textQuery(
                searchSummary,
                buildFilters(params, false),
                pageSizeOrDefault(params),
                null
        );

        List<VisionSearchResult> results = searchOrchestrator.search(query).stream()
                .filter(r -> !r.propertyId().equals(params.referencePropertyId()))
                .toList();
        return ToolResult.success(results);
    }

    private VisionSearchFilters buildFilters(Params params, boolean includeScenes) {
        if (params == null) {
            return new VisionSearchFilters(null, null, null, null, null, null, null, null, null);
        }
        return new VisionSearchFilters(
                params.city(),
                params.country(),
                null,
                null,
                params.minGuests(),
                params.maxPricePerNight(),
                null,
                includeScenes ? params.requiredSceneTypes() : null,
                null
        );
    }

    private int pageSizeOrDefault(Params params) {
        return params != null && params.pageSize() != null && params.pageSize() > 0 ? Math.min(params.pageSize(), 25) : 10;
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(Params.class);
    }
}
