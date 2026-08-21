package com.luna.aggarly.vision.vector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.property.entity.Property;
import com.luna.aggarly.property.repository.PropertyRepository;
import com.luna.aggarly.vision.entity.PropertyImageAiMetadata;
import com.luna.aggarly.vision.entity.PropertyVisualProfile;
import com.luna.aggarly.vision.entity.enums.ImageQualityGrade;
import com.luna.aggarly.vision.entity.enums.ModerationStatus;
import com.luna.aggarly.vision.entity.enums.ProfileStatus;
import com.luna.aggarly.vision.entity.enums.SceneType;
import com.luna.aggarly.vision.pipeline.records.DetectedAmenity;
import com.luna.aggarly.vision.pipeline.records.StyleTag;
import com.luna.aggarly.vision.repository.PropertyImageAiMetadataRepository;
import com.luna.aggarly.vision.repository.PropertyVisualProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyVisualProfileAggregator {

    private final PropertyRepository propertyRepository;
    private final PropertyImageAiMetadataRepository metadataRepository;
    private final PropertyVisualProfileRepository profileRepository;
    private final MultimodalEmbeddingService embeddingService;
    private final QdrantVisionClient qdrantClient;
    private final ObjectMapper objectMapper;

    @Value("${aggarly.qdrant.collections.profiles:property_visual_profiles_v1}")
    private String profilesCollection = "property_visual_profiles_v1";

    @Value("${aggarly.vision.profile.min-usable-images:3}")
    private int minUsableImages = 3;

    @Value("${aggarly.vision.models.embedding.dimension:768}")
    private int vectorDim = 768;

    private static final List<SceneType> KEY_ROOMS = List.of(
            SceneType.BEDROOM, SceneType.BATHROOM, SceneType.KITCHEN, SceneType.LIVING_ROOM, SceneType.EXTERIOR
    );

    @Transactional
    public PropertyVisualProfile aggregatePropertyProfile(UUID propertyId) {
        log.info("Aggregating PropertyVisualProfile for propertyId={}", propertyId);

        Property property = propertyRepository.findById(propertyId).orElse(null);
        if (property == null) {
            log.warn("Property not found for visual profile aggregation: {}", propertyId);
            return null;
        }

        List<PropertyImageAiMetadata> allMetadata = metadataRepository.findByPropertyId(propertyId);
        int totalImages = allMetadata.size();

        List<PropertyImageAiMetadata> usable = allMetadata.stream()
                .filter(m -> m.getModerationStatus() == ModerationStatus.APPROVED)
                .filter(m -> m.getQualityGrade() != ImageQualityGrade.REJECTED)
                .toList();

        int usableImages = usable.size();
        int processedImages = (int) allMetadata.stream()
                .filter(m -> m.getProcessingStatus() != null && "COMPLETED".equals(m.getProcessingStatus().name()))
                .count();

        PropertyVisualProfile profile = profileRepository.findByPropertyId(propertyId)
                .orElseGet(() -> PropertyVisualProfile.builder()
                        .property(property)
                        .build());

        profile.setTotalImages(totalImages);
        profile.setProcessedImages(processedImages);
        profile.setUsableImages(usableImages);

        // Room Coverage & Best Image Per Scene
        Map<String, Integer> roomCoverage = new HashMap<>();
        Map<String, String> bestPerScene = new HashMap<>();
        Map<String, Double> bestSceneScores = new HashMap<>();
        List<String> missingKeyRooms = new ArrayList<>();
        Set<SceneType> presentRooms = new HashSet<>();

        UUID recommendedCoverImageId = null;
        double highestCoverScore = -1.0;

        Set<String> aggregatedStyleTags = new HashSet<>();
        Set<String> aggregatedAmenities = new HashSet<>();
        List<String> representativeImageIds = new ArrayList<>();

        double sumRomantic = 0.0, sumLuxury = 0.0, sumFamily = 0.0, sumBusiness = 0.0, sumRelaxation = 0.0;

        for (PropertyImageAiMetadata meta : usable) {
            if (meta.getSceneType() != null) {
                String stName = meta.getSceneType().name();
                presentRooms.add(meta.getSceneType());
                roomCoverage.put(stName, roomCoverage.getOrDefault(stName, 0) + 1);

                double qScore = meta.getQualityScore() != null ? meta.getQualityScore() : 0.5;
                if (qScore > bestSceneScores.getOrDefault(stName, -1.0)) {
                    bestSceneScores.put(stName, qScore);
                    if (meta.getPropertyImage() != null) {
                        bestPerScene.put(stName, meta.getPropertyImage().getId().toString());
                    }
                }

                // Cover image candidate score: bonus for Exterior/Living/Pool with high quality
                double coverCandidateScore = qScore;
                if (meta.getSceneType() == SceneType.EXTERIOR || meta.getSceneType() == SceneType.POOL || meta.getSceneType() == SceneType.VIEW) {
                    coverCandidateScore += 0.30;
                }
                if (coverCandidateScore > highestCoverScore && meta.getPropertyImage() != null) {
                    highestCoverScore = coverCandidateScore;
                    recommendedCoverImageId = meta.getPropertyImage().getId();
                }
            }

            if (meta.getPropertyImage() != null) {
                representativeImageIds.add(meta.getPropertyImage().getId().toString());
            }

            // Extract tags
            deserializeAndCollectTags(meta.getStyleTagsJson(), aggregatedStyleTags);
            deserializeAndCollectAmenities(meta.getDetectedAmenitiesJson(), aggregatedAmenities);

            // Accumulate visual fit scores
            if (meta.getVisualFitJson() != null) {
                try {
                    Map<String, Double> fit = objectMapper.readValue(meta.getVisualFitJson(), Map.class);
                    sumRomantic += fit.getOrDefault("romantic", 0.0);
                    sumLuxury += fit.getOrDefault("luxury", 0.0);
                    sumFamily += fit.getOrDefault("family", 0.0);
                    sumBusiness += fit.getOrDefault("business", 0.0);
                    sumRelaxation += fit.getOrDefault("relaxation", 0.0);
                } catch (Exception ignored) {}
            }
        }

        for (SceneType keyRoom : KEY_ROOMS) {
            if (!presentRooms.contains(keyRoom)) {
                missingKeyRooms.add(keyRoom.name());
            }
        }

        double coverageScore = KEY_ROOMS.isEmpty() ? 1.0 : (double) (KEY_ROOMS.size() - missingKeyRooms.size()) / KEY_ROOMS.size();
        profile.setCoverageScore(coverageScore);

        profile.setRecommendedCoverImageId(recommendedCoverImageId);

        if (usableImages > 0) {
            profile.setRomanticScore(Math.min(1.0, sumRomantic / usableImages));
            profile.setLuxuryScore(Math.min(1.0, sumLuxury / usableImages));
            profile.setFamilyScore(Math.min(1.0, sumFamily / usableImages));
            profile.setBusinessScore(Math.min(1.0, sumBusiness / usableImages));
            profile.setRelaxationScore(Math.min(1.0, sumRelaxation / usableImages));
        }

        // Build rich visual summary
        String summaryText = String.format("A visually diverse property featuring %d usable photos with confirmed amenities: %s. Aesthetic style: %s. Room coverage includes: %s.",
                usableImages,
                aggregatedAmenities,
                aggregatedStyleTags,
                roomCoverage.keySet()
        );
        profile.setVisualSummary(summaryText);

        try {
            profile.setRoomCoverageJson(objectMapper.writeValueAsString(roomCoverage));
            profile.setBestPerSceneJson(objectMapper.writeValueAsString(bestPerScene));
            profile.setMissingKeyRoomsJson(objectMapper.writeValueAsString(missingKeyRooms));
            profile.setRepresentativeImageIdsJson(objectMapper.writeValueAsString(representativeImageIds));
            profile.setAggregatedStyleTagsJson(objectMapper.writeValueAsString(aggregatedStyleTags));
            profile.setAggregatedAmenitiesJson(objectMapper.writeValueAsString(aggregatedAmenities));
        } catch (Exception e) {
            log.warn("Error serializing visual profile JSON fields: {}", e.getMessage());
        }

        profile.setProfileStatus(usableImages >= minUsableImages ? ProfileStatus.READY : ProfileStatus.INCOMPLETE);
        profile.setLastAggregatedAt(Instant.now());

        // Generate Qdrant Centroid Multi-Vector Representation
        float[] centroidCaptionVector = embeddingService.embedText(summaryText);
        float[] centroidImageVector = new float[vectorDim];
        for (int i = 0; i < vectorDim; i++) {
            centroidImageVector[i] = centroidCaptionVector[i]; // aligned space
        }

        Map<String, float[]> namedCentroidVectors = Map.of(
                "centroid_image_vector", centroidImageVector,
                "centroid_caption_vector", centroidCaptionVector
        );

        Map<String, Object> payload = new HashMap<>();
        payload.put("propertyId", propertyId.toString());
        payload.put("coverageScore", coverageScore);
        payload.put("profileStatus", profile.getProfileStatus().name());
        payload.put("imageCount", usableImages);
        payload.put("city", property.getAddress() != null ? property.getAddress().getCity() : "");
        payload.put("country", property.getAddress() != null ? property.getAddress().getCountry() : "");
        payload.put("pricePerNight", property.getBasePricePerNight() != null ? property.getBasePricePerNight().doubleValue() : 0.0);
        payload.put("maxGuests", property.getMaxGuests() != 0 ? property.getMaxGuests() : 2);

        qdrantClient.upsertMultiVectorPoint(profilesCollection, propertyId, namedCentroidVectors, payload);

        profile.setQdrantPointId(propertyId);
        profile.setQdrantCollection(profilesCollection);
        profile.setEmbeddedAt(Instant.now());

        PropertyVisualProfile saved = profileRepository.save(profile);
        log.info("Visual profile aggregated and indexed for propertyId={}, status={}", propertyId, saved.getProfileStatus());
        return saved;
    }

    private void deserializeAndCollectTags(String json, Set<String> target) {
        if (json == null || json.isBlank()) return;
        try {
            List<StyleTag> tags = objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, StyleTag.class));
            for (StyleTag t : tags) {
                target.add(t.tag());
            }
        } catch (Exception ignored) {}
    }

    private void deserializeAndCollectAmenities(String json, Set<String> target) {
        if (json == null || json.isBlank()) return;
        try {
            List<DetectedAmenity> amenities = objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, DetectedAmenity.class));
            for (DetectedAmenity a : amenities) {
                target.add(a.amenityName());
            }
        } catch (Exception ignored) {}
    }
}
