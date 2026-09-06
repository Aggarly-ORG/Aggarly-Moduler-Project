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

    @Test
    @DisplayName("VisionSearchEvaluator computes accurate NDCG@10, Recall, and Filter Correctness across multi-modal queries")
    void testVisionSearchEvaluatorExecution() throws Exception {
        VisionSearchEvaluator evaluator = new VisionSearchEvaluator(
                evalQueryRepository,
                evaluationRunRepository,
                searchOrchestrator,
                candidateMerger,
                propertyRepository,
                objectMapper,
                fileStorageService
        );

        UUID prop1 = UUID.randomUUID();
        UUID prop2 = UUID.randomUUID();
        UUID prop3 = UUID.randomUUID();

        // Seed 3 test queries: 1 text, 1 image, 1 multimodal
        VisionEvalQuery textQ = VisionEvalQuery.builder()
                .id(UUID.randomUUID())
                .queryText("luxury cliffside villa with infinity pool")
                .queryType("TEXT_ONLY")
                .city("Santorini")
                .expectedPropertyIdsJson(objectMapper.writeValueAsString(List.of(prop1.toString(), prop2.toString())))
                .relevanceGradesJson(objectMapper.writeValueAsString(Map.of(prop1.toString(), 3, prop2.toString(), 2)))
                .build();

        VisionEvalQuery imageQ = VisionEvalQuery.builder()
                .id(UUID.randomUUID())
                .referenceImageKey("properties/gt-chalet-exterior.jpg")
                .queryType("IMAGE_ONLY")
                .expectedPropertyIdsJson(objectMapper.writeValueAsString(List.of(prop2.toString())))
                .relevanceGradesJson(objectMapper.writeValueAsString(Map.of(prop2.toString(), 3)))
                .build();

        VisionEvalQuery multiQ = VisionEvalQuery.builder()
                .id(UUID.randomUUID())
                .referenceImageKey("properties/gt-manhattan-skyline.jpg")
                .queryText("modern penthouse under $1000 in New York")
                .queryType("MULTIMODAL")
                .city("New York")
                .maxPricePerNight(1000.0)
                .expectedPropertyIdsJson(objectMapper.writeValueAsString(List.of(prop3.toString())))
                .relevanceGradesJson(objectMapper.writeValueAsString(Map.of(prop3.toString(), 3)))
                .build();

        when(evalQueryRepository.findAll()).thenReturn(List.of(textQ, imageQ, multiQ));

        VisionSearchResult res1 = new VisionSearchResult(
                prop1, "Villa Azure", "Santorini", "Greece", 650.0, 6,
                0.95f, 0.95f, 0.90f, UUID.randomUUID(), "http://img1.jpg", "POOL",
                "Modern villa", List.of(), List.of(), 0.95
        );
        VisionSearchResult res2 = new VisionSearchResult(
                prop2, "Chalet Peak", "Zermatt", "Switzerland", 850.0, 8,
                0.82f, 0.82f, 0.80f, UUID.randomUUID(), "http://img2.jpg", "EXTERIOR",
                "Alpine chalet", List.of(), List.of(), 0.90
        );
        VisionSearchResult res3 = new VisionSearchResult(
                prop3, "The Obsidian Tower", "New York", "United States", 950.0, 10,
                0.98f, 0.98f, 0.95f, UUID.randomUUID(), "http://img3.jpg", "LIVING_ROOM",
                "Duplex penthouse", List.of(), List.of(), 0.99
        );

        when(searchOrchestrator.search(any(VisionSearchQuery.class))).thenAnswer(invocation -> {
            VisionSearchQuery q = invocation.getArgument(0);
            if (q.hardFilters() != null && "Santorini".equals(q.hardFilters().city())) {
                return List.of(res1, res2);
            } else if (q.hardFilters() != null && "New York".equals(q.hardFilters().city())) {
                return List.of(res3);
            } else {
                return List.of(res2);
            }
        });

        Property mockProp1 = Property.builder().title("Villa Azure").basePricePerNight(BigDecimal.valueOf(650.0)).maxGuests(6).build();
        Property mockProp2 = Property.builder().title("Chalet Peak").basePricePerNight(BigDecimal.valueOf(850.0)).maxGuests(8).build();
        Property mockProp3 = Property.builder().title("The Obsidian Tower").basePricePerNight(BigDecimal.valueOf(950.0)).maxGuests(10).build();
        when(propertyRepository.findById(prop1)).thenReturn(Optional.of(mockProp1));
        when(propertyRepository.findById(prop2)).thenReturn(Optional.of(mockProp2));
        when(propertyRepository.findById(prop3)).thenReturn(Optional.of(mockProp3));
        when(candidateMerger.passesHardConstraints(any(), any())).thenReturn(true);

        EvaluationReport report = evaluator.evaluate(new EvaluationConfig(10, false, false, 0));

        assertNotNull(report);
        assertEquals(3, report.totalQueriesEvaluated());
        assertTrue(report.meanNdcgAt10() > 0.80, "Mean NDCG@10 should be high for top-ranked matches");
        assertEquals(1.0, report.filterCorrectness(), "Filter correctness must be 1.00 when all candidates satisfy constraints");

        // Verify evaluation run entity was saved
        verify(evaluationRunRepository, times(1)).save(any());
    }
}
