package com.luna.aggarly.vision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.property.entity.Property;
import com.luna.aggarly.property.entity.PropertyImage;
import com.luna.aggarly.property.repository.PropertyImageRepository;
import com.luna.aggarly.property.repository.PropertyRepository;
import com.luna.aggarly.vision.dto.PhotoTourWalkthroughResponse;
import com.luna.aggarly.vision.entity.PropertyImageAiMetadata;
import com.luna.aggarly.vision.entity.enums.ImageQualityGrade;
import com.luna.aggarly.vision.entity.enums.SceneType;
import com.luna.aggarly.vision.repository.PropertyImageAiMetadataRepository;
import com.luna.aggarly.vision.repository.PropertyVisualProfileRepository;
import com.luna.aggarly.vision.service.impl.PhotoTourSequencingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PhotoTourSequencingTest {

    @Mock
    private PropertyRepository propertyRepository;
    @Mock
    private PropertyImageRepository propertyImageRepository;
    @Mock
    private PropertyImageAiMetadataRepository aiMetadataRepository;
    @Mock
    private PropertyVisualProfileRepository visualProfileRepository;

    private PhotoTourSequencingServiceImpl sequencingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        sequencingService = new PhotoTourSequencingServiceImpl(
                propertyRepository,
                propertyImageRepository,
                aiMetadataRepository,
                visualProfileRepository,
                objectMapper
        );
    }

    @Test
    @DisplayName("Photo tour generates topological room ordering: Exterior -> Living Room -> Bedroom -> Pool")
    void photoTourSequencesRoomsTopologically() {
        UUID propertyId = UUID.randomUUID();
        Property property = Property.builder().title("Villa Sapphire").build();
        property.setId(propertyId);
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));

        // Create images across various scenes
        PropertyImage imgPool = PropertyImage.builder().id(UUID.randomUUID()).objectKey("properties/pool.jpg").build();
        PropertyImage imgBed = PropertyImage.builder().id(UUID.randomUUID()).objectKey("properties/bed.jpg").build();
        PropertyImage imgExt = PropertyImage.builder().id(UUID.randomUUID()).objectKey("properties/ext.jpg").build();
        PropertyImage imgLiv = PropertyImage.builder().id(UUID.randomUUID()).objectKey("properties/liv.jpg").build();

        PropertyImageAiMetadata metaPool = PropertyImageAiMetadata.builder()
                .id(UUID.randomUUID())
                .propertyImage(imgPool)
                .sceneType(SceneType.POOL)
                .qualityScore(0.92)
                .qualityGrade(ImageQualityGrade.EXCELLENT)
                .detectedAmenitiesJson("[\"infinity_pool\", \"sunbeds\"]")
                .build();

        PropertyImageAiMetadata metaBed = PropertyImageAiMetadata.builder()
                .id(UUID.randomUUID())
                .propertyImage(imgBed)
                .sceneType(SceneType.BEDROOM)
                .qualityScore(0.88)
                .qualityGrade(ImageQualityGrade.GOOD)
                .detectedAmenitiesJson("[\"king_bed\", \"sea_view\"]")
                .build();

        PropertyImageAiMetadata metaExt = PropertyImageAiMetadata.builder()
                .id(UUID.randomUUID())
                .propertyImage(imgExt)
                .sceneType(SceneType.EXTERIOR)
                .qualityScore(0.95)
                .qualityGrade(ImageQualityGrade.EXCELLENT)
                .build();

        PropertyImageAiMetadata metaLiv = PropertyImageAiMetadata.builder()
                .id(UUID.randomUUID())
                .propertyImage(imgLiv)
                .sceneType(SceneType.LIVING_ROOM)
                .qualityScore(0.90)
                .qualityGrade(ImageQualityGrade.GOOD)
                .detectedAmenitiesJson("[\"fireplace\", \"smart_tv\"]")
                .build();

        when(aiMetadataRepository.findUsableByPropertyId(propertyId))
                .thenReturn(List.of(metaPool, metaBed, metaExt, metaLiv));

        PhotoTourWalkthroughResponse response = sequencingService.generatePhotoTour(propertyId);

        assertNotNull(response);
        assertEquals(propertyId, response.propertyId());
        assertEquals("Villa Sapphire", response.propertyTitle());
        assertEquals(4, response.totalScenes());

        // Order must strictly be: EXTERIOR (10) -> LIVING_ROOM (20) -> BEDROOM (50) -> POOL (80)
        assertEquals(SceneType.EXTERIOR, response.scenes().get(0).sceneType());
        assertEquals(SceneType.LIVING_ROOM, response.scenes().get(1).sceneType());
        assertEquals(SceneType.BEDROOM, response.scenes().get(2).sceneType());
        assertEquals(SceneType.POOL, response.scenes().get(3).sceneType());

        // Verified amenities aggregation
        assertTrue(response.highlightedAmenities().contains("infinity_pool"));
        assertTrue(response.highlightedAmenities().contains("sea_view"));
        assertTrue(response.highlightedAmenities().contains("fireplace"));
    }

    @Test
    @DisplayName("Fallback photo tour builds scenes from PropertyImages when AI metadata is pending")
    void fallbackTourBuildsFromPropertyImages() {
        UUID propertyId = UUID.randomUUID();
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.empty());
        when(aiMetadataRepository.findUsableByPropertyId(propertyId)).thenReturn(List.of());
        when(aiMetadataRepository.findByPropertyId(propertyId)).thenReturn(List.of());

        PropertyImage img1 = PropertyImage.builder().id(UUID.randomUUID()).objectKey("properties/1.jpg").build();
        PropertyImage img2 = PropertyImage.builder().id(UUID.randomUUID()).objectKey("properties/2.jpg").build();

        when(propertyImageRepository.findByPropertyIdOrderByDisplayOrderAsc(propertyId))
                .thenReturn(List.of(img1, img2));

        PhotoTourWalkthroughResponse response = sequencingService.generatePhotoTour(propertyId);

        assertNotNull(response);
        assertEquals(2, response.totalScenes());
        assertEquals("properties/1.jpg", response.scenes().get(0).imageUrl());
        assertEquals("properties/2.jpg", response.scenes().get(1).imageUrl());
    }
}
