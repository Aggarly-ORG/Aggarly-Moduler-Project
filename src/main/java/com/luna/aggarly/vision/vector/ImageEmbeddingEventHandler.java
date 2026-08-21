package com.luna.aggarly.vision.vector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.filestorage.service.FileStorageService;
import com.luna.aggarly.vision.entity.PropertyImageAiMetadata;
import com.luna.aggarly.vision.entity.enums.ImageQualityGrade;
import com.luna.aggarly.vision.entity.enums.ModerationStatus;
import com.luna.aggarly.vision.entity.enums.VisionIndexState;
import com.luna.aggarly.vision.event.PropertyImageAnalyzedEvent;
import com.luna.aggarly.vision.event.PropertyImageEmbeddedEvent;
import com.luna.aggarly.vision.repository.PropertyImageAiMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Event listener that generates and indexes multi-vector points (image_vector & caption_vector) into Qdrant.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageEmbeddingEventHandler {

    private final MultimodalEmbeddingService embeddingService;
    private final QdrantVisionClient qdrantClient;
    private final PropertyImageAiMetadataRepository metadataRepository;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Value("${aggarly.qdrant.collections.images:property_images_v1}")
    private String imagesCollection = "property_images_v1";

    @Async("visionWorkerExecutor")
    @EventListener
    @Transactional
    public void onPropertyImageAnalyzed(PropertyImageAnalyzedEvent event) {
        log.info("Processing embedding for analyzed image: imageId={}, propertyId={}", event.imageId(), event.propertyId());

        PropertyImageAiMetadata metadata = metadataRepository.findByPropertyImageId(event.imageId()).orElse(null);
        if (metadata == null) {
            log.warn("Metadata not found for imageId: {}", event.imageId());
            return;
        }

        if (metadata.getModerationStatus() != ModerationStatus.APPROVED || metadata.getQualityGrade() == ImageQualityGrade.REJECTED) {
            log.info("Skipping Qdrant indexing for rejected/unmoderated image: {}", event.imageId());
            metadata.setVectorIndexState(VisionIndexState.NOT_INDEXED);
            metadataRepository.save(metadata);
            return;
        }

        metadata.setVectorIndexState(VisionIndexState.INDEXING);
        metadataRepository.save(metadata);

        try {
            // Load image bytes
            byte[] imageBytes = new byte[0];
            try (InputStream is = fileStorageService.getFileStream(event.objectKey())) {
                if (is != null) {
                    imageBytes = is.readAllBytes();
                }
            } catch (Exception e) {
                log.warn("Could not load image bytes for embedding, generating text-based vector: {}", e.getMessage());
            }

            final byte[] finalBytes = imageBytes;

            // Generate vectors concurrently
            CompletableFuture<float[]> imageVecFuture = CompletableFuture.supplyAsync(() ->
                    embeddingService.embedImage(finalBytes, metadata.getPerceptualHash()));

            CompletableFuture<float[]> captionVecFuture = CompletableFuture.supplyAsync(() -> {
                String caption = metadata.getAiCaption();
                String alt = metadata.getAltText();
                String summary = metadata.getVisualSummary();
                if ((caption == null || caption.isBlank()) && (summary == null || summary.isBlank())) {
                    caption = String.format("%s with %s view, %s quality",
                            metadata.getSceneType() != null ? metadata.getSceneType().name() : "ROOM",
                            metadata.getViewType() != null ? metadata.getViewType().name() : "NONE",
                            metadata.getQualityGrade() != null ? metadata.getQualityGrade().name() : "GOOD"
                    );
                }
                return embeddingService.embedCaption(caption, alt, summary);
            });

            CompletableFuture.allOf(imageVecFuture, captionVecFuture).join();

            float[] imageVector = imageVecFuture.get();
            float[] captionVector = captionVecFuture.get();

            Map<String, float[]> namedVectors = Map.of(
                    "image_vector", imageVector,
                    "caption_vector", captionVector
            );

            // Build Rich Qdrant Payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("propertyId", event.propertyId().toString());
            payload.put("imageId", event.imageId().toString());
            payload.put("sceneType", metadata.getSceneType() != null ? metadata.getSceneType().name() : "BEDROOM");
            payload.put("isIndoor", metadata.getIndoor() != null ? metadata.getIndoor() : true);
            payload.put("viewType", metadata.getViewType() != null ? metadata.getViewType().name() : "NONE");
            payload.put("isCover", metadata.getPropertyImage() != null && metadata.getPropertyImage().isCover());
            payload.put("qualityGrade", metadata.getQualityGrade() != null ? metadata.getQualityGrade().name() : "ACCEPTABLE");
            payload.put("qualityScore", metadata.getQualityScore() != null ? metadata.getQualityScore() : 0.80);
            payload.put("moderationStatus", metadata.getModerationStatus().name());
            payload.put("aiCaption", metadata.getAiCaption() != null ? metadata.getAiCaption() : "");
            payload.put("altText", metadata.getAltText() != null ? metadata.getAltText() : "");

            if (metadata.getRoomClusterId() != null) {
                payload.put("roomClusterId", metadata.getRoomClusterId());
            }

            if (metadata.getDetectedAmenitiesJson() != null) {
                try {
                    payload.put("detectedAmenities", objectMapper.readValue(metadata.getDetectedAmenitiesJson(), List.class));
                } catch (Exception ignored) {}
            }

            if (metadata.getStyleTagsJson() != null) {
                try {
                    payload.put("styleTags", objectMapper.readValue(metadata.getStyleTagsJson(), List.class));
                } catch (Exception ignored) {}
            }

            // Upsert into Qdrant multi-vector collection
            qdrantClient.upsertMultiVectorPoint(imagesCollection, event.imageId(), namedVectors, payload);

            // Update database state
            metadata.setQdrantPointId(event.imageId());
            metadata.setQdrantCollection(imagesCollection);
            metadata.setEmbeddedAt(Instant.now());
            metadata.setVectorIndexState(VisionIndexState.INDEXED);
            metadataRepository.save(metadata);

            log.info("Successfully indexed image {} into Qdrant collection {}", event.imageId(), imagesCollection);

            eventPublisher.publishEvent(new PropertyImageEmbeddedEvent(event.imageId(), event.propertyId(), event.imageId()));
        } catch (Exception e) {
            log.error("Failed to generate/upsert embeddings for image {}: {}", event.imageId(), e.getMessage(), e);
            metadata.setVectorIndexState(VisionIndexState.FAILED);
            metadataRepository.save(metadata);
        }
    }
}
