package com.luna.aggarly.vision.vector;

import com.luna.aggarly.vision.entity.PropertyImageAiMetadata;
import com.luna.aggarly.vision.entity.enums.VisionIndexState;
import com.luna.aggarly.vision.event.PropertyImageAnalyzedEvent;
import com.luna.aggarly.vision.repository.PropertyImageAiMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingMigrationService {

    private final PropertyImageAiMetadataRepository metadataRepository;
    private final PropertyVisualProfileAggregator profileAggregator;
    private final PropertyDescriptionEmbeddingService descriptionEmbeddingService;
    private final ApplicationEventPublisher eventPublisher;

    @Async("visionWorkerExecutor")
    @Transactional(readOnly = true)
    public void migrateImage(UUID imageId) {
        PropertyImageAiMetadata meta = metadataRepository.findByPropertyImageIdWithDetails(imageId).orElse(null);
        if (meta != null && meta.getPropertyImage() != null) {
            log.info("Migrating embedding for image: {}", imageId);
            eventPublisher.publishEvent(new PropertyImageAnalyzedEvent(
                    imageId,
                    meta.getProperty() != null ? meta.getProperty().getId() : null,
                    meta.getPropertyImage().getObjectKey(),
                    true
            ));
        }
    }

    @Async("visionWorkerExecutor")
    @Transactional(readOnly = true)
    public void migrateAllProperties(int batchSize) {
        log.info("Starting bulk embedding migration for all property records");
        List<PropertyImageAiMetadata> all = metadataRepository.findAllWithImages();
        Set<UUID> propertyIds = new HashSet<>();

        for (PropertyImageAiMetadata meta : all) {
            if (meta.getPropertyImage() != null) {
                try {
                    eventPublisher.publishEvent(new PropertyImageAnalyzedEvent(
                            meta.getPropertyImage().getId(),
                            meta.getProperty() != null ? meta.getProperty().getId() : null,
                            meta.getPropertyImage().getObjectKey(),
                            true
                    ));
                    if (meta.getProperty() != null) {
                        propertyIds.add(meta.getProperty().getId());
                    }
                } catch (Exception e) {
                    log.warn("Failed to publish migration event for image {}: {}", meta.getPropertyImage().getId(), e.getMessage());
                }
            }
        }

        for (UUID propId : propertyIds) {
            try {
                descriptionEmbeddingService.embedAndUpsertProperty(propId);
                profileAggregator.aggregatePropertyProfile(propId);
            } catch (Exception e) {
                log.warn("Error migrating property description/profile for {}: {}", propId, e.getMessage());
            }
        }
    }
}
