package com.luna.aggarly.vision.vector;

import com.luna.aggarly.vision.entity.PropertyImageAiMetadata;
import com.luna.aggarly.vision.event.PropertyDeletedEvent;
import com.luna.aggarly.vision.event.PropertyImageDeletedEvent;
import com.luna.aggarly.vision.repository.PropertyImageAiMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VisionVectorCleanupService {

    private final QdrantVisionClient qdrantClient;
    private final PropertyImageAiMetadataRepository metadataRepository;

    @Value("${aggarly.qdrant.collections.images:property_images_v1}")
    private String imagesCollection = "property_images_v1";

    @Value("${aggarly.qdrant.collections.profiles:property_visual_profiles_v1}")
    private String profilesCollection = "property_visual_profiles_v1";

    @Value("${aggarly.qdrant.collections.descriptions:property_descriptions_v1}")
    private String descriptionsCollection = "property_descriptions_v1";

    public void removeImagePoint(UUID imageId) {
        log.info("Deleting image vector point {} from Qdrant", imageId);
        qdrantClient.deletePoint(imagesCollection, imageId);
    }

    public void removePropertyPoints(UUID propertyId) {
        log.info("Deleting all vector points for property {} from Qdrant", propertyId);
        List<PropertyImageAiMetadata> list = metadataRepository.findByPropertyId(propertyId);
        for (PropertyImageAiMetadata meta : list) {
            if (meta.getPropertyImage() != null) {
                qdrantClient.deletePoint(imagesCollection, meta.getPropertyImage().getId());
            }
        }
        qdrantClient.deletePoint(profilesCollection, propertyId);
        qdrantClient.deletePoint(descriptionsCollection, propertyId);
    }

    @Async("visionWorkerExecutor")
    @EventListener
    public void onImageDeleted(PropertyImageDeletedEvent event) {
        removeImagePoint(event.imageId());
    }

    @Async("visionWorkerExecutor")
    @EventListener
    public void onPropertyDeleted(PropertyDeletedEvent event) {
        removePropertyPoints(event.propertyId());
    }
}
