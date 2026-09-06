package com.luna.aggarly.vision.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.user.security.UserPrincipal;
import com.luna.aggarly.vision.dto.HostCoverageReportDto;
import com.luna.aggarly.vision.dto.HostImprovementAdviceDto;
import com.luna.aggarly.vision.dto.PropertyImageMetadataDto;
import com.luna.aggarly.vision.dto.PropertyVisualProfileDto;
import com.luna.aggarly.vision.entity.PropertyImageAiMetadata;
import com.luna.aggarly.vision.entity.PropertyVisualProfile;
import com.luna.aggarly.vision.entity.enums.ImageQualityGrade;
import com.luna.aggarly.vision.repository.PropertyImageAiMetadataRepository;
import com.luna.aggarly.vision.repository.PropertyVisualProfileRepository;
import com.luna.aggarly.vision.vector.PropertyVisualProfileAggregator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * Controller providing visual listing profile intelligence, room coverage reports, and improvement recommendations for hosts.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/vision/")
@RequiredArgsConstructor
@Tag(name = "Vision Host Intelligence", description = "Host Visual Profile, Coverage & Quality Advice APIs")
public class AiMetaDataController{
    public final PropertyImageAiMetadataRepository metadataRepository;
    public final ObjectMapper objectMapper;

    @GetMapping("/image/{imageId}/metadata")
    @Operation(summary = "Get AI visual metadata for a single property image", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<PropertyImageMetadataDto>> getImageMetadata(@PathVariable UUID imageId) {
        PropertyImageAiMetadata meta = metadataRepository.findByPropertyImageId(imageId).orElse(null);
        if (meta == null) {
            return ApiResponse.<PropertyImageMetadataDto>notFound("Image metadata not found").toResponseEntity();
        }

        PropertyImageMetadataDto dto = mapImageToDto(meta);
        return ApiResponse.ok(dto, "Image AI metadata retrieved").toResponseEntity();
    }

    private PropertyVisualProfileDto mapProfileToDto(PropertyVisualProfile profile) {
        if (profile == null) return null;
        Map<String, Integer> roomCoverage = Map.of();
        Map<String, String> bestPerScene = Map.of();
        List<String> missingRooms = List.of();
        List<String> repImages = List.of();
        List<String> styles = List.of();
        List<String> amenities = List.of();

        try {
            if (profile.getRoomCoverageJson() != null) roomCoverage = objectMapper.readValue(profile.getRoomCoverageJson(), Map.class);
            if (profile.getBestPerSceneJson() != null) bestPerScene = objectMapper.readValue(profile.getBestPerSceneJson(), Map.class);
            if (profile.getMissingKeyRoomsJson() != null) missingRooms = objectMapper.readValue(profile.getMissingKeyRoomsJson(), List.class);
            if (profile.getRepresentativeImageIdsJson() != null) repImages = objectMapper.readValue(profile.getRepresentativeImageIdsJson(), List.class);
            if (profile.getAggregatedStyleTagsJson() != null) styles = objectMapper.readValue(profile.getAggregatedStyleTagsJson(), List.class);
            if (profile.getAggregatedAmenitiesJson() != null) amenities = objectMapper.readValue(profile.getAggregatedAmenitiesJson(), List.class);
        } catch (Exception ignored) {}

        return new PropertyVisualProfileDto(
                profile.getProperty().getId(),
                profile.getProfileStatus().name(),
                profile.getTotalImages(),
                profile.getProcessedImages(),
                profile.getUsableImages(),
                profile.getCoverageScore(),
                roomCoverage,
                bestPerScene,
                missingRooms,
                profile.getRecommendedCoverImageId(),
                repImages,
                profile.getRomanticScore(),
                profile.getLuxuryScore(),
                profile.getFamilyScore(),
                profile.getBusinessScore(),
                profile.getRelaxationScore(),
                styles,
                amenities,
                profile.getVisualSummary()
        );
    }

    private PropertyImageMetadataDto mapImageToDto(PropertyImageAiMetadata meta) {
        List<Object> objects = List.of();
        List<Object> amenities = List.of();
        List<Object> styles = List.of();
        List<String> colors = List.of();
        Map<String, Object> condition = Map.of();

        try {
            if (meta.getDetectedObjectsJson() != null) objects = objectMapper.readValue(meta.getDetectedObjectsJson(), List.class);
            if (meta.getDetectedAmenitiesJson() != null) amenities = objectMapper.readValue(meta.getDetectedAmenitiesJson(), List.class);
            if (meta.getStyleTagsJson() != null) styles = objectMapper.readValue(meta.getStyleTagsJson(), List.class);
            if (meta.getDominantColorsJson() != null) colors = objectMapper.readValue(meta.getDominantColorsJson(), List.class);
            if (meta.getConditionAssessmentJson() != null) condition = objectMapper.readValue(meta.getConditionAssessmentJson(), Map.class);
        } catch (Exception ignored) {}

        return new PropertyImageMetadataDto(
                meta.getPropertyImage() != null ? meta.getPropertyImage().getId() : meta.getId(),
                meta.getProperty().getId(),
                meta.getProcessingStatus().name(),
                meta.getCurrentStage(),
                meta.getSceneType() != null ? meta.getSceneType().name() : "OTHER",
                meta.getSceneConfidence(),
                meta.getIndoor(),
                meta.getViewType() != null ? meta.getViewType().name() : "NONE",
                meta.getAiCaption(),
                meta.getAltText(),
                meta.getQualityGrade() != null ? meta.getQualityGrade().name() : "ACCEPTABLE",
                meta.getQualityScore(),
                meta.getTechnicalQualityScore(),
                meta.getVisualUsabilityScore(),
                meta.getSearchabilityScore(),
                meta.getPerceptualHash(),
                meta.getDuplicateClassification() != null ? meta.getDuplicateClassification().name() : "UNIQUE",
                objects,
                amenities,
                styles,
                colors,
                condition,
                meta.getVisualSummary(),
                meta.getModerationStatus().name()
        );
    }
}
