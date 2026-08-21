package com.luna.aggarly.vision.config;

import com.luna.aggarly.vision.vector.EmbeddingMigrationService;
import com.luna.aggarly.vision.vector.QdrantVisionClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class QdrantCollectionInitializer implements ApplicationRunner {

    private final QdrantVisionClient qdrantClient;
    private final VisionModelSeeder modelSeeder;
    private final EmbeddingMigrationService embeddingMigrationService;

    @Value("${aggarly.qdrant.collections.images:property_images_v1}")
    private String imagesCollection = "property_images_v1";

    @Value("${aggarly.qdrant.collections.profiles:property_visual_profiles_v1}")
    private String profilesCollection = "property_visual_profiles_v1";

    @Value("${aggarly.qdrant.collections.descriptions:property_descriptions_v1}")
    private String descriptionsCollection = "property_descriptions_v1";

    @Value("${aggarly.vision.models.embedding.dimension:768}")
    private int vectorDim = 768;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Running QdrantCollectionInitializer");
        try {
            // 1. Multi-vector image collection (image_vector + caption_vector)
            Map<String, Integer> imageNamedVectors = Map.of(
                    "image_vector", vectorDim,
                    "caption_vector", vectorDim
            );
            qdrantClient.ensureMultiVectorCollectionExists(imagesCollection, imageNamedVectors);

            // 2. Multi-vector profile collection (centroid_image_vector + centroid_caption_vector)
            Map<String, Integer> profileNamedVectors = Map.of(
                    "centroid_image_vector", vectorDim,
                    "centroid_caption_vector", vectorDim
            );
            qdrantClient.ensureMultiVectorCollectionExists(profilesCollection, profileNamedVectors);

            // 3. Single-vector property description collection
            Map<String, Integer> descNamedVectors = Map.of(
                    "description_vector", vectorDim
            );
            qdrantClient.ensureMultiVectorCollectionExists(descriptionsCollection, descNamedVectors);

            // 4. Seed default models
            modelSeeder.seedDefaultModels();

            // 5. Auto-sync existing database embeddings to Qdrant
            embeddingMigrationService.migrateAllProperties(100);

            log.info("QdrantCollectionInitializer completed successfully");
        } catch (Exception e) {
            log.warn("QdrantCollectionInitializer finished with warning: {}", e.getMessage());
        }
    }
}
