package com.luna.aggarly.vision.vector;

import com.luna.aggarly.property.entity.Property;
import com.luna.aggarly.property.repository.PropertyRepository;
import com.luna.aggarly.vision.event.PropertyCreatedEvent;
import com.luna.aggarly.vision.event.PropertyTextUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyDescriptionEmbeddingService {

    private final PropertyRepository propertyRepository;
    private final MultimodalEmbeddingService embeddingService;
    private final QdrantVisionClient qdrantClient;

    @Value("${aggarly.qdrant.collections.descriptions:property_descriptions_v1}")
    private String descriptionsCollection = "property_descriptions_v1";

    @Transactional(readOnly = true)
    public void embedAndUpsertProperty(UUID propertyId) {
        log.info("Generating description vector for propertyId={}", propertyId);
        Property property = propertyRepository.findById(propertyId).orElse(null);
        if (property == null) {
            log.warn("Property not found for description embedding: {}", propertyId);
            return;
        }

        try {
            float[] descriptionVector = embeddingService.embedPropertyDescription(
                    property.getTitle(),
                    property.getDescription(),
                    property.getAmenities() != null ? property.getAmenities().toString() : "",
                    property.getAddress() != null ? property.getAddress().getCity() + ", " + property.getAddress().getCountry() : ""
            );

            Map<String, float[]> namedVectors = Map.of("description_vector", descriptionVector);

            Map<String, Object> payload = new HashMap<>();
            payload.put("propertyId", property.getId().toString());
            payload.put("city", property.getAddress() != null && property.getAddress().getCity() != null ? property.getAddress().getCity().trim().toLowerCase() : "");
            payload.put("country", property.getAddress() != null && property.getAddress().getCountry() != null ? property.getAddress().getCountry().trim().toLowerCase() : "");
            payload.put("pricePerNight", property.getBasePricePerNight() != null ? property.getBasePricePerNight().doubleValue() : 0.0);
            payload.put("maxGuests", property.getMaxGuests() != 0 ? property.getMaxGuests() : 2);
            payload.put("propertyType", property.getPropertyType() != null ? property.getPropertyType().name() : "APARTMENT");
            payload.put("isActive", property.getStatus() != null && "ACTIVE".equalsIgnoreCase(property.getStatus().name()));

            qdrantClient.upsertMultiVectorPoint(descriptionsCollection, property.getId(), namedVectors, payload);
            log.info("Successfully upserted description vector for property: {}", propertyId);
        } catch (Exception e) {
            log.error("Error generating/upserting description vector for property {}: {}", propertyId, e.getMessage(), e);
        }
    }

    @Async("visionWorkerExecutor")
    @EventListener
    public void onPropertyCreated(PropertyCreatedEvent event) {
        embedAndUpsertProperty(event.propertyId());
    }

    @Async("visionWorkerExecutor")
    @EventListener
    public void onPropertyTextUpdated(PropertyTextUpdatedEvent event) {
        embedAndUpsertProperty(event.propertyId());
    }
}
