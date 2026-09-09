package com.luna.aggarly.vision.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.chat.entity.enums.MessageType;
import com.luna.aggarly.chat.service.ChatAiBridgeService;
import com.luna.aggarly.chat.service.MessageService;
import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.property.entity.Amenity;
import com.luna.aggarly.property.repository.AmenityRepository;
import com.luna.aggarly.user.security.UserPrincipal;
import com.luna.aggarly.vision.dto.FastVisionAnalysisRequest;
import com.luna.aggarly.vision.dto.FastVisionAnalysisResponse;
import com.luna.aggarly.vision.dto.UiCommandDto;
import com.luna.aggarly.vision.pipeline.ConceptNormalizer;
import com.luna.aggarly.vision.pipeline.ImagePreprocessor;
import com.luna.aggarly.vision.pipeline.VisionInferenceEngine;
import com.luna.aggarly.vision.pipeline.records.DetectedAmenity;
import com.luna.aggarly.vision.pipeline.records.NormalizedVisionResult;
import com.luna.aggarly.vision.pipeline.records.PreprocessedImage;
import com.luna.aggarly.vision.pipeline.records.StyleTag;
import com.luna.aggarly.vision.pipeline.records.VisionInferenceResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * Controller executing rapid photo-by-photo visual perception for host listing creation and management.
 * Connects vision inference directly to the UI Commands Agent and dedicated property conversation.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/vision")
@RequiredArgsConstructor
@Tag(name = "Fast Vision Analysis", description = "Instant Multi-Modal Perception & UI Commands for Sanctuary Wizard")
public class FastVisionController {

    private final ImagePreprocessor imagePreprocessor;
    private final VisionInferenceEngine visionInferenceEngine;
    private final ConceptNormalizer conceptNormalizer;
    private final AmenityRepository amenityRepository;
    private final MessageService messageService;
    private final ObjectMapper objectMapper;

    @PostMapping("/fast-analyze")
    @Operation(summary = "Perform fast visual perception on an uploaded photo and emit UI commands",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<FastVisionAnalysisResponse>> fastAnalyze(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody FastVisionAnalysisRequest request) {

        log.info("Fast vision perception triggered for imageKey: {}, conversation: {}, user: {}",
                request.imageKey(), request.conversationId(), principal != null ? principal.getUserId() : "anonymous");

        UUID imageId = UUID.randomUUID();

        // 1. Preprocess the image bytes from storage
        PreprocessedImage preprocessed = imagePreprocessor.preprocess(imageId, request.propertyId(), request.imageKey());

        // 2. Execute VLM inference (Ollama or fallback heuristic)
        VisionInferenceResult rawInference = visionInferenceEngine.infer(preprocessed, null);

        // 3. Normalize architectural and amenity concepts
        NormalizedVisionResult normalized = conceptNormalizer.normalize(rawInference);

        // 4. Map detected visual concepts to Aggarly's canonical Amenity catalog
        List<Amenity> allAmenities = amenityRepository.findAll();
        List<Amenity> matchedAmenities = new ArrayList<>();
        Set<UUID> matchedAmenityIds = new HashSet<>();
        List<String> detectedAmenityNames = new ArrayList<>();

        List<DetectedAmenity> visionAmenities = normalized.normalizedAmenities() != null && !normalized.normalizedAmenities().isEmpty()
                ? normalized.normalizedAmenities()
                : (rawInference.detectedAmenities() != null ? rawInference.detectedAmenities() : List.of());

        for (DetectedAmenity da : visionAmenities) {
            String rawName = da.amenityName();
            if (rawName == null || rawName.isBlank()) continue;

            Amenity matched = findMatchingAmenity(rawName, allAmenities);
            if (matched != null && !matchedAmenityIds.contains(matched.getId())) {
                matchedAmenityIds.add(matched.getId());
                matchedAmenities.add(matched);
                detectedAmenityNames.add(matched.getName());
            } else if (matched == null && !detectedAmenityNames.contains(rawName)) {
                detectedAmenityNames.add(rawName);
            }
        }

        // 5. Generate UI Agent Commands
        List<UiCommandDto> uiCommands = new ArrayList<>();

        // Auto-check amenities in the wizard
        for (Amenity amenity : matchedAmenities) {
            uiCommands.add(UiCommandDto.builder()
                    .type("CLICK_AMENITY")
                    .target(amenity.getId().toString())
                    .value(amenity.getName())
                    .description("Auto-checked '" + amenity.getName() + "' detected in " + (request.fileName() != null ? request.fileName() : "photo"))
                    .build());
        }

        // Scene-based form field updates
        String sceneType = normalized.raw().sceneType() != null ? normalized.raw().sceneType() : "OTHER";
        if (sceneType.equalsIgnoreCase("BEDROOM")) {
            uiCommands.add(UiCommandDto.builder()
                    .type("UPDATE_FIELD")
                    .target("bedrooms")
                    .value(1)
                    .description("Confirmed Bedroom scene: Suggested allocating bedroom count")
                    .build());
        } else if (sceneType.equalsIgnoreCase("BATHROOM")) {
            uiCommands.add(UiCommandDto.builder()
                    .type("UPDATE_FIELD")
                    .target("bathrooms")
                    .value(1)
                    .description("Confirmed Bathroom scene: Suggested allocating bathroom count")
                    .build());
        }

        if (normalized.raw().aiCaption() != null && !normalized.raw().aiCaption().isBlank()) {
            uiCommands.add(UiCommandDto.builder()
                    .type("SUGGEST_CONTENT")
                    .target("curatorial_caption")
                    .value(normalized.raw().aiCaption())
                    .description("Curated architectural caption")
                    .build());
        }

        List<String> styleNames = new ArrayList<>();
        if (normalized.normalizedStyleTags() != null) {
            for (StyleTag st : normalized.normalizedStyleTags()) {
                if (st.tag() != null && !styleNames.contains(st.tag())) {
                    styleNames.add(st.tag());
                }
            }
        }

        String architecturalSummary = normalized.raw().aiCaption() != null
                ? normalized.raw().aiCaption()
                : (sceneType + " sanctuary space with natural illumination.");

        // 6. Persist Action Card into Dedicated Property Conversation
        if (request.conversationId() != null) {
            try {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("cardType", "FAST_VISION_ANALYSIS");
                metadata.put("imageKey", request.imageKey());
                metadata.put("imageUrl", request.imageUrl());
                metadata.put("fileName", request.fileName());
                metadata.put("sceneType", sceneType);
                metadata.put("sceneConfidence", normalized.raw().sceneConfidence());
                metadata.put("aiCaption", normalized.raw().aiCaption());
                metadata.put("detectedAmenities", detectedAmenityNames);
                metadata.put("detectedAmenityIds", matchedAmenities.stream().map(a -> a.getId().toString()).toList());
                metadata.put("styleTags", styleNames);
                metadata.put("uiCommands", uiCommands);
                metadata.put("skipAiTurn", true);

                String metadataJson = objectMapper.writeValueAsString(metadata);

                StringBuilder sb = new StringBuilder();
                sb.append("📷 **Visual Perception Completed for ").append(request.fileName() != null ? request.fileName() : "Photo").append("**\n\n");
                sb.append("• **Scene**: ").append(sceneType)
                  .append(" (").append((int)(normalized.raw().sceneConfidence() * 100)).append("% confidence)\n");
                if (normalized.raw().aiCaption() != null && !normalized.raw().aiCaption().isBlank()) {
                    sb.append("• **Editorial Caption**: ").append(normalized.raw().aiCaption()).append("\n");
                }
                if (!detectedAmenityNames.isEmpty()) {
                    sb.append("• **Detected Amenities**: ").append(String.join(", ", detectedAmenityNames)).append("\n");
                }
                if (!styleNames.isEmpty()) {
                    sb.append("• **Architectural Cues**: ").append(String.join(", ", styleNames)).append("\n");
                }
                if (!uiCommands.isEmpty()) {
                    sb.append("\n⚡ *UI Commands Agent auto-checked ").append(matchedAmenities.size()).append(" matching amenities in your sanctuary draft.*");
                }

                messageService.sendMessage(
                        request.conversationId(),
                        ChatAiBridgeService.AI_BOT_SYSTEM_ID,
                        sb.toString(),
                        MessageType.ACTION_CARD,
                        metadataJson
                );
                log.info("Persisted fast vision card to property conversation {}", request.conversationId());
            } catch (Exception e) {
                log.warn("Failed to persist vision message to property conversation {}: {}", request.conversationId(), e.getMessage());
            }
        }

        FastVisionAnalysisResponse response = FastVisionAnalysisResponse.builder()
                .imageId(imageId)
                .imageKey(request.imageKey())
                .imageUrl(request.imageUrl())
                .sceneType(sceneType)
                .sceneConfidence(normalized.raw().sceneConfidence())
                .isIndoor(normalized.raw().isIndoor())
                .viewType(normalized.raw().viewType())
                .aiCaption(normalized.raw().aiCaption())
                .detectedAmenities(detectedAmenityNames)
                .detectedAmenityIds(matchedAmenities.stream().map(Amenity::getId).toList())
                .styleTags(styleNames)
                .dominantColors(normalized.raw().dominantColors())
                .architecturalSummary(architecturalSummary)
                .uiCommands(uiCommands)
                .build();

        return ApiResponse.ok(response, "Fast vision perception and UI commands generated").toResponseEntity();
    }

    private Amenity findMatchingAmenity(String rawCandidate, List<Amenity> allAmenities) {
        String query = rawCandidate.trim().toLowerCase(Locale.ROOT);

        // 1. Exact match
        for (Amenity a : allAmenities) {
            if (a.getName().trim().equalsIgnoreCase(query)) {
                return a;
            }
        }

        // 2. Heuristic alias dictionary
        Map<String, List<String>> aliases = Map.ofEntries(
                Map.entry("pool", List.of("pool", "swimming", "infinity", "plunge")),
                Map.entry("wifi", List.of("wifi", "wi-fi", "internet")),
                Map.entry("terrace", List.of("terrace", "balcony", "patio", "deck", "veranda")),
                Map.entry("sea view", List.of("ocean", "sea", "waterfront", "coast")),
                Map.entry("mountain view", List.of("mountain", "alpine", "cliff")),
                Map.entry("kitchen", List.of("kitchen", "cooktop", "culinary")),
                Map.entry("air conditioning", List.of("air condition", "ac", "climate")),
                Map.entry("hot tub", List.of("hot tub", "jacuzzi", "spa")),
                Map.entry("fireplace", List.of("fireplace", "hearth")),
                Map.entry("workspace", List.of("workspace", "desk", "office")),
                Map.entry("gym", List.of("gym", "fitness", "workout")),
                Map.entry("king bed", List.of("king", "queen", "bed", "master"))
        );

        for (Amenity a : allAmenities) {
            String nameLower = a.getName().toLowerCase(Locale.ROOT);

            // If name contains candidate or candidate contains name
            if (nameLower.contains(query) || query.contains(nameLower)) {
                return a;
            }

            // Check aliases
            for (Map.Entry<String, List<String>> entry : aliases.entrySet()) {
                boolean candidateMatchesAlias = entry.getValue().stream().anyMatch(query::contains);
                boolean amenityMatchesAlias = entry.getValue().stream().anyMatch(nameLower::contains);
                if (candidateMatchesAlias && amenityMatchesAlias) {
                    return a;
                }
            }
        }

        return null;
    }
}
