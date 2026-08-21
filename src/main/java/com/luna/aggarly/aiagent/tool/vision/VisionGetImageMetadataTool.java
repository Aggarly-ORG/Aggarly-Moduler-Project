package com.luna.aggarly.aiagent.tool.vision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import com.luna.aggarly.vision.entity.PropertyImageAiMetadata;
import com.luna.aggarly.vision.repository.PropertyImageAiMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class VisionGetImageMetadataTool implements Tool<VisionGetImageMetadataTool.Params, VisionGetImageMetadataTool.ImageMetadataResponse> {

    private final PropertyImageAiMetadataRepository metadataRepository;
    private final ObjectMapper objectMapper;

    public record Params(
            UUID imageId
    ) {}

    public record ImageMetadataResponse(
            UUID imageId,
            UUID propertyId,
            String sceneType,
            Double sceneConfidence,
            Boolean isIndoor,
            String viewType,
            String aiCaption,
            String altText,
            String qualityGrade,
            Double qualityScore,
            List<Object> detectedObjects,
            List<Object> detectedAmenities,
            List<Object> styleTags,
            List<String> dominantColors,
            Map<String, Object> conditionAssessment,
            String visualSummary
    ) {}

    @Override
    public String name() {
        return "vision.getImageMetadata";
    }

    @Override
    public String description() {
        return "Get the full AI-generated perceptual metadata for a specific property photo (scene type, objects, style tags, condition report).";
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
    public ToolResult<ImageMetadataResponse> execute(Params params, UserPrincipal currentUser) {
        log.info("Executing tool vision.getImageMetadata for imageId: {}", params.imageId());
        PropertyImageAiMetadata metadata = metadataRepository.findByPropertyImageId(params.imageId()).orElse(null);
        if (metadata == null) {
            return ToolResult.failure("AI metadata not found for image: " + params.imageId());
        }

        List<Object> objects = List.of();
        List<Object> amenities = List.of();
        List<Object> styles = List.of();
        List<String> colors = List.of();
        Map<String, Object> condition = Map.of();

        try {
            if (metadata.getDetectedObjectsJson() != null) objects = objectMapper.readValue(metadata.getDetectedObjectsJson(), List.class);
            if (metadata.getDetectedAmenitiesJson() != null) amenities = objectMapper.readValue(metadata.getDetectedAmenitiesJson(), List.class);
            if (metadata.getStyleTagsJson() != null) styles = objectMapper.readValue(metadata.getStyleTagsJson(), List.class);
            if (metadata.getDominantColorsJson() != null) colors = objectMapper.readValue(metadata.getDominantColorsJson(), List.class);
            if (metadata.getConditionAssessmentJson() != null) condition = objectMapper.readValue(metadata.getConditionAssessmentJson(), Map.class);
        } catch (Exception ignored) {}

        ImageMetadataResponse response = new ImageMetadataResponse(
                metadata.getPropertyImage() != null ? metadata.getPropertyImage().getId() : metadata.getId(),
                metadata.getProperty().getId(),
                metadata.getSceneType() != null ? metadata.getSceneType().name() : "OTHER",
                metadata.getSceneConfidence(),
                metadata.getIndoor(),
                metadata.getViewType() != null ? metadata.getViewType().name() : "NONE",
                metadata.getAiCaption(),
                metadata.getAltText(),
                metadata.getQualityGrade() != null ? metadata.getQualityGrade().name() : "ACCEPTABLE",
                metadata.getQualityScore(),
                objects,
                amenities,
                styles,
                colors,
                condition,
                metadata.getVisualSummary()
        );

        return ToolResult.success(response);
    }
}
