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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller providing visual listing profile intelligence, room coverage reports, and improvement recommendations for hosts.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/vision/host")
@RequiredArgsConstructor
@Tag(name = "Vision Host Intelligence", description = "Host Visual Profile, Coverage & Quality Advice APIs")
public class VisionHostController {

    private final PropertyVisualProfileRepository profileRepository;
    private final PropertyImageAiMetadataRepository metadataRepository;
    private final PropertyVisualProfileAggregator profileAggregator;
    private final ObjectMapper objectMapper;

    @PreAuthorize("hasAnyRole('HOST','ADMIN')")
    @GetMapping("/property/{propertyId}/profile")
    @Operation(summary = "Get aggregated visual intelligence profile for a property", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<PropertyVisualProfileDto>> getVisualProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID propertyId) {
        PropertyVisualProfile profile = profileRepository.findByPropertyId(propertyId).orElse(null);
        if (profile == null) {
            profile = profileAggregator.aggregatePropertyProfile(propertyId);
        }

        PropertyVisualProfileDto dto = mapProfileToDto(profile);
        return ApiResponse.ok(dto, "Property visual profile retrieved successfully").toResponseEntity();
    }

    @PreAuthorize("hasAnyRole('HOST','ADMIN')")
    @GetMapping("/property/{propertyId}/coverage")
    @Operation(summary = "Get room coverage analysis and missing scene recommendations", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<HostCoverageReportDto>> getCoverageReport(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID propertyId) {
        PropertyVisualProfile profile = profileRepository.findByPropertyId(propertyId).orElse(null);
        if (profile == null) {
            profile = profileAggregator.aggregatePropertyProfile(propertyId);
        }

        Map<String, Integer> roomCoverage = Map.of();
        List<String> missingRooms = List.of();
        List<String> recommendations = new ArrayList<>();

        if (profile != null) {
            try {
                if (profile.getRoomCoverageJson() != null) roomCoverage = objectMapper.readValue(profile.getRoomCoverageJson(), Map.class);
                if (profile.getMissingKeyRoomsJson() != null) missingRooms = objectMapper.readValue(profile.getMissingKeyRoomsJson(), List.class);
            } catch (Exception ignored) {}

            for (String missing : missingRooms) {
                recommendations.add("Upload high-resolution photos of the " + missing.toLowerCase() + " to improve search conversion.");
            }
            if (profile.getUsableImages() < 5) {
                recommendations.add("Add at least " + (5 - profile.getUsableImages()) + " more verified photos to achieve maximum visual search score.");
            }
        }

        HostCoverageReportDto report = new HostCoverageReportDto(
                propertyId,
                profile != null && profile.getCoverageScore() != null ? profile.getCoverageScore() : 0.0,
                roomCoverage,
                missingRooms,
                recommendations
        );

        return ApiResponse.ok(report, "Host room coverage report generated").toResponseEntity();
    }

    @PreAuthorize("hasAnyRole('HOST','ADMIN')")
    @GetMapping("/property/{propertyId}/advice")
    @Operation(summary = "Get actionable photography and visual quality advice", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<HostImprovementAdviceDto>> getImprovementAdvice(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID propertyId) {
        List<PropertyImageAiMetadata> images = metadataRepository.findByPropertyId(propertyId);
        List<String> lowQualityImages = new ArrayList<>();
        List<String> suggestedAngles = List.of("Daylight panoramic view of master bedroom", "Wide-angle kitchen with amenities in frame", "Exterior facade during golden hour");

        for (PropertyImageAiMetadata img : images) {
            if (img.getQualityGrade() == ImageQualityGrade.POOR || img.isBlurry() || img.isDark()) {
                lowQualityImages.add("Image " + (img.getPropertyImage() != null ? img.getPropertyImage().getId() : img.getId()) + ": low lighting or blur detected");
            }
        }

        PropertyVisualProfile profile = profileRepository.findByPropertyId(propertyId).orElse(null);

        HostImprovementAdviceDto advice = new HostImprovementAdviceDto(
                propertyId,
                "Optimize your listing presentation with bright natural light and comprehensive room coverage.",
                suggestedAngles,
                lowQualityImages,
                profile != null ? profile.getRecommendedCoverImageId() : null
        );

        return ApiResponse.ok(advice, "Host improvement advice generated").toResponseEntity();
    }

    @PreAuthorize("hasAnyRole('HOST','ADMIN')")
    @PostMapping("/property/{propertyId}/reaggregate")
    @Operation(summary = "Trigger immediate re-aggregation of property visual intelligence", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<PropertyVisualProfileDto>> reaggregateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID propertyId) {
        PropertyVisualProfile profile = profileAggregator.aggregatePropertyProfile(propertyId);
        PropertyVisualProfileDto dto = mapProfileToDto(profile);
        return ApiResponse.ok(dto, "Visual profile successfully reaggregated").toResponseEntity();
    }

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
