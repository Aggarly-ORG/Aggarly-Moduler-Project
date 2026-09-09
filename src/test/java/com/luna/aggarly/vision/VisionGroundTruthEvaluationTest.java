package com.luna.aggarly.vision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.property.entity.Address;
import com.luna.aggarly.property.entity.Property;
import com.luna.aggarly.property.entity.enums.PropertyStatus;
import com.luna.aggarly.property.entity.enums.PropertyType;
import com.luna.aggarly.property.repository.PropertyImageRepository;
import com.luna.aggarly.property.repository.PropertyRepository;
import com.luna.aggarly.user.entity.User;
import com.luna.aggarly.user.repository.UserRepository;
import com.luna.aggarly.vision.entity.VisionEvalQuery;
import com.luna.aggarly.vision.entity.enums.SceneType;
import com.luna.aggarly.vision.evaluation.VisionGroundTruthSeederService;
import com.luna.aggarly.vision.evaluation.VisionSearchEvaluator;
import com.luna.aggarly.vision.evaluation.records.EvaluationConfig;
import com.luna.aggarly.vision.evaluation.records.EvaluationReport;
import com.luna.aggarly.vision.repository.PropertyImageAiMetadataRepository;
import com.luna.aggarly.vision.repository.VisionEvalQueryRepository;
import com.luna.aggarly.vision.repository.VisionEvaluationRunRepository;
import com.luna.aggarly.vision.search.SearchCandidateMerger;
import com.luna.aggarly.vision.search.VisionSearchOrchestrator;
import com.luna.aggarly.vision.search.records.VisionSearchQuery;
import com.luna.aggarly.vision.search.records.VisionSearchResult;
import com.luna.aggarly.vision.vector.MultimodalEmbeddingService;
import com.luna.aggarly.vision.vector.PropertyDescriptionEmbeddingService;
import com.luna.aggarly.vision.vector.PropertyVisualProfileAggregator;
import com.luna.aggarly.vision.vector.QdrantVisionClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VisionGroundTruthEvaluationTest {

    @Mock
    private PropertyRepository propertyRepository;
    @Mock
    private PropertyImageRepository propertyImageRepository;
    @Mock
    private PropertyImageAiMetadataRepository metadataRepository;
    @Mock
    private VisionEvalQueryRepository evalQueryRepository;
    @Mock
    private VisionEvaluationRunRepository evaluationRunRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MultimodalEmbeddingService embeddingService;
    @Mock
    private com.luna.aggarly.vision.pipeline.PerceptualHashService perceptualHashService;
    @Mock
    private QdrantVisionClient qdrantClient;
    @Mock
    private PropertyDescriptionEmbeddingService descriptionEmbeddingService;
    @Mock
    private PropertyVisualProfileAggregator profileAggregator;
    @Mock
    private VisionSearchOrchestrator searchOrchestrator;
    @Mock
    private SearchCandidateMerger candidateMerger;
    @Mock
    private com.luna.aggarly.filestorage.service.FileStorageService fileStorageService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("Ground truth seeder accurately constructs 10 properties, 110 photos, and 30 evaluation queries")
    void testGroundTruthSeederDatasetIntegrity() {
        VisionGroundTruthSeederService seeder = new VisionGroundTruthSeederService(
                propertyRepository,
                propertyImageRepository,
                metadataRepository,
                evalQueryRepository,
                userRepository,
                embeddingService,
                perceptualHashService,
                qdrantClient,
                descriptionEmbeddingService,
                profileAggregator,
                objectMapper
        );

        User mockUser = User.builder().email("test-host@aggarly.com").build();
        mockUser.setId(UUID.randomUUID());
        when(userRepository.findByEmail("test-host@aggarly.com")).thenReturn(Optional.of(mockUser));

        when(propertyRepository.save(any(Property.class))).thenAnswer(invocation -> {
            Property p = invocation.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        when(propertyImageRepository.save(any())).thenAnswer(invocation -> {
            com.luna.aggarly.property.entity.PropertyImage pi = invocation.getArgument(0);
            pi.setId(UUID.randomUUID());
            return pi;
        });

        when(embeddingService.embedImage(any(), any())).thenReturn(new float[768]);
        when(embeddingService.embedCaption(any(), any(), any())).thenReturn(new float[768]);

        VisionGroundTruthSeederService.SeedingSummary summary = seeder.seedGroundTruthDataset();

        assertNotNull(summary);
        assertEquals(10, summary.propertiesCreated(), "Must create exactly 10 test properties");
        assertEquals(110, summary.imagesCreated(), "Must create exactly 110 room/exterior photos (11 per property)");
        assertEquals(30, summary.queriesCreated(), "Must create exactly 30 evaluation queries");

        // Verify representative titles
        assertTrue(summary.propertyTitles().contains("Villa Azure Santorini — Cliffside Luxury Cave Villa with Heated Infinity Pool"));
        assertTrue(summary.propertyTitles().contains("Chalet Zermatt Peak — Luxury Alpine Timber Chalet with Matterhorn View & Sauna"));
        assertTrue(summary.propertyTitles().contains("The Obsidian Tower — Ultra-Modern Tribeca Duplex Penthouse with Private Skyline Terrace"));
        assertTrue(summary.propertyTitles().contains("Bambu Indah Retreat — Curved Bamboo Architectural Sanctuary with Private Jungle Pool"));
        assertTrue(summary.propertyTitles().contains("Gion Kyo-Machiya — Preserved Historic Wooden Townhouse with Zen Garden & Cedar Onsen Tub"));

        // Verify property repository save was called 10 times
        verify(propertyRepository, times(10)).save(any(Property.class));
        // Verify property images saved 110 times
        verify(propertyImageRepository, times(110)).save(any());
        // Verify metadata saved 110 times
        verify(metadataRepository, times(110)).save(any());
        // Verify eval queries saved once in bulk
        verify(evalQueryRepository, times(1)).saveAll(any());
    }

}
