package com.luna.aggarly.vision.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.property.entity.Property;
import com.luna.aggarly.property.entity.PropertyImage;
import com.luna.aggarly.property.repository.PropertyImageRepository;
import com.luna.aggarly.property.repository.PropertyRepository;
import com.luna.aggarly.vision.dto.PhotoTourSceneDto;
import com.luna.aggarly.vision.dto.PhotoTourWalkthroughResponse;
import com.luna.aggarly.vision.entity.PropertyImageAiMetadata;
import com.luna.aggarly.vision.entity.PropertyVisualProfile;
import com.luna.aggarly.vision.entity.enums.ImageQualityGrade;
import com.luna.aggarly.vision.entity.enums.SceneType;
import com.luna.aggarly.vision.repository.PropertyImageAiMetadataRepository;
import com.luna.aggarly.vision.repository.PropertyVisualProfileRepository;
import com.luna.aggarly.vision.service.PhotoTourSequencingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoTourSequencingServiceImpl implements PhotoTourSequencingService {

    private final PropertyRepository propertyRepository;
    private final PropertyImageRepository propertyImageRepository;
    private final PropertyImageAiMetadataRepository aiMetadataRepository;
    private final PropertyVisualProfileRepository visualProfileRepository;
    private final ObjectMapper objectMapper;

    private static final Map<SceneType, Integer> NATURAL_SCENE_ORDER = Map.ofEntries(
            Map.entry(SceneType.EXTERIOR, 10),
            Map.entry(SceneType.LIVING_ROOM, 20),
            Map.entry(SceneType.DINING, 30),
            Map.entry(SceneType.KITCHEN, 40),
            Map.entry(SceneType.BEDROOM, 50),
            Map.entry(SceneType.BATHROOM, 60),
            Map.entry(SceneType.BALCONY, 70),
            Map.entry(SceneType.POOL, 80),
            Map.entry(SceneType.VIEW, 90),
            Map.entry(SceneType.WORKSPACE, 100),
            Map.entry(SceneType.OTHER, 110)
    );

    @Override
    @Transactional(readOnly = true)
    public PhotoTourWalkthroughResponse generatePhotoTour(UUID propertyId) {
        log.info("Generating sequenced photo tour for property {}", propertyId);

        Property property = propertyRepository.findById(propertyId).orElse(null);
        String title = property != null ? property.getTitle() : "Property Walkthrough";

        List<PropertyImageAiMetadata> metadataList = aiMetadataRepository.findUsableByPropertyId(propertyId);
        if (metadataList.isEmpty()) {
            metadataList = aiMetadataRepository.findByPropertyId(propertyId);
        }

        // Fallback: If no AI metadata exists yet, build basic scenes directly from PropertyImages
        if (metadataList.isEmpty()) {
            List<PropertyImage> rawImages = propertyImageRepository.findByPropertyIdOrderByDisplayOrderAsc(propertyId);
            return buildFallbackTour(propertyId, title, rawImages);
        }

        // Filter out rejected or duplicate images
        List<PropertyImageAiMetadata> validMetadata = metadataList.stream()
                .filter(m -> m.getQualityGrade() != ImageQualityGrade.REJECTED)
                .filter(m -> !m.isScreenshot() && !m.isCollage())
                .toList();

        if (validMetadata.isEmpty()) {
            validMetadata = metadataList;
        }

        // Group by roomClusterId (or sceneType if roomClusterId is absent)
        Map<String, List<PropertyImageAiMetadata>> clustered = new LinkedHashMap<>();
        for (PropertyImageAiMetadata m : validMetadata) {
            String clusterKey = (m.getRoomClusterId() != null && !m.getRoomClusterId().isBlank())
                    ? m.getRoomClusterId()
                    : (m.getSceneType() != null ? m.getSceneType().name() : "ROOM_" + m.getId().toString().substring(0, 4));
            clustered.computeIfAbsent(clusterKey, k -> new ArrayList<>()).add(m);
        }

        // For each cluster, pick the best representative image (highest quality score or technical score)
        List<PropertyImageAiMetadata> representatives = new ArrayList<>();
        for (List<PropertyImageAiMetadata> roomImages : clustered.values()) {
            PropertyImageAiMetadata best = roomImages.stream()
                    .max(Comparator.comparingDouble(m -> m.getQualityScore() != null ? m.getQualityScore() : 0.0))
                    .orElse(roomImages.get(0));
            representatives.add(best);
        }

        // Sort representative scenes topologically according to NATURAL_SCENE_ORDER
        representatives.sort(Comparator.comparingInt(m -> getSceneOrder(m.getSceneType())));

        // Build scene DTOs
        List<PhotoTourSceneDto> sceneDtos = new ArrayList<>();
        Set<String> allVerifiedAmenities = new LinkedHashSet<>();
        int seq = 1;

        for (PropertyImageAiMetadata m : representatives) {
            List<String> roomAmenities = parseJsonList(m.getDetectedAmenitiesJson());
            List<String> styleTags = parseJsonList(m.getStyleTagsJson());
            allVerifiedAmenities.addAll(roomAmenities);

            String roomName = formatRoomName(m.getSceneType(), m.getRoomClusterId(), seq);
            PropertyImage img = m.getPropertyImage();
            String key = img != null ? img.getObjectKey() : null;

            sceneDtos.add(new PhotoTourSceneDto(
                    img != null ? img.getId() : m.getId(),
                    propertyId,
                    key,
                    key,
                    m.getSceneType() != null ? m.getSceneType() : SceneType.OTHER,
                    m.getRoomClusterId(),
                    roomName,
                    seq++,
                    m.getQualityScore(),
                    m.getQualityGrade(),
                    m.getViewType(),
                    m.getAiCaption() != null ? m.getAiCaption() : m.getHostCaption(),
                    roomAmenities,
                    styleTags
            ));
        }

        // Get visual summary from profile if available
        String summary = null;
        Optional<PropertyVisualProfile> profileOpt = visualProfileRepository.findByPropertyId(propertyId);
        if (profileOpt.isPresent()) {
            summary = profileOpt.get().getVisualSummary();
        }
        if (summary == null || summary.isBlank()) {
            summary = String.format("Complete %d-scene tour featuring %s.",
                    sceneDtos.size(),
                    allVerifiedAmenities.isEmpty() ? "modern living spaces" : String.join(", ", allVerifiedAmenities.stream().limit(4).toList()));
        }

        return new PhotoTourWalkthroughResponse(
                propertyId,
                title,
                sceneDtos.size(),
                sceneDtos,
                new ArrayList<>(allVerifiedAmenities),
                summary
        );
    }

    private PhotoTourWalkthroughResponse buildFallbackTour(UUID propertyId, String title, List<PropertyImage> images) {
        List<PhotoTourSceneDto> scenes = new ArrayList<>();
        int seq = 1;
        for (PropertyImage img : images) {
            scenes.add(new PhotoTourSceneDto(
                    img.getId(),
                    propertyId,
                    img.getObjectKey(),
                    img.getObjectKey(),
                    SceneType.OTHER,
                    "ROOM_" + seq,
                    "Scene " + seq,
                    seq++,
                    0.85,
                    ImageQualityGrade.GOOD,
                    null,
                    "Scene " + seq,
                    List.of(),
                    List.of()
            ));
        }
        return new PhotoTourWalkthroughResponse(
                propertyId,
                title,
                scenes.size(),
                scenes,
                List.of(),
                "Property Photo Gallery"
        );
    }

    private int getSceneOrder(SceneType sceneType) {
        if (sceneType == null) return 120;
        return NATURAL_SCENE_ORDER.getOrDefault(sceneType, 110);
    }

    private String formatRoomName(SceneType sceneType, String clusterId, int index) {
        if (sceneType == null) return "Room " + index;
        return switch (sceneType) {
            case EXTERIOR -> "Exterior & Architecture";
            case LIVING_ROOM -> "Living Room & Lounge";
            case DINING -> "Dining Area";
            case KITCHEN -> "Modern Kitchen";
            case BEDROOM -> "Master Bedroom";
            case BATHROOM -> "En-suite Bathroom";
            case BALCONY -> "Private Balcony & Terrace";
            case POOL -> "Pool & Outdoor Lounge";
            case VIEW -> "Panoramic View";
            case WORKSPACE -> "Dedicated Workspace";
            case OTHER -> "Interior Space";
        };
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
