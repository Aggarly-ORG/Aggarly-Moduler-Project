package com.luna.aggarly.vision.config;

import com.luna.aggarly.vision.entity.VisionModelRegistry;
import com.luna.aggarly.vision.entity.enums.ModelModality;
import com.luna.aggarly.vision.entity.enums.VisionModelTaskType;
import com.luna.aggarly.vision.repository.VisionModelRegistryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class VisionModelSeeder {

    private final VisionModelRegistryRepository modelRegistryRepository;

    public void seedDefaultModels() {
        if (modelRegistryRepository.count() == 0) {
            log.info("Seeding default vision model entries in vision_model_registry");

            // 1. Perception VLM (MiniMax M3 Cloud / SOTA Multimodal)
            modelRegistryRepository.save(VisionModelRegistry.builder()
                    .modelName("minimax-m3:cloud")
                    .modelVersion("1.0.0")
                    .taskType(VisionModelTaskType.CLASSIFICATION)
                    .provider("OLLAMA")
                    .modality(ModelModality.IMAGE_TEXT)
                    .promptVersion("2.1.0")
                    .preprocessingVersion("1.0.0")
                    .active(true)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build());

            // 2. Multimodal Embedding (Image & Text aligned)
            modelRegistryRepository.save(VisionModelRegistry.builder()
                    .modelName("nomic-embed-text")
                    .modelVersion("1.5.0")
                    .taskType(VisionModelTaskType.EMBEDDING)
                    .embeddingDim(768)
                    .provider("NOMIC")
                    .modality(ModelModality.IMAGE_TEXT)
                    .promptVersion("1.0.0")
                    .preprocessingVersion("1.0.0")
                    .active(true)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build());

            // 3. Reranker
            modelRegistryRepository.save(VisionModelRegistry.builder()
                    .modelName("gemma3:4b")
                    .modelVersion("1.0.0")
                    .taskType(VisionModelTaskType.RERANKING)
                    .provider("OLLAMA")
                    .modality(ModelModality.TEXT)
                    .promptVersion("1.0.0")
                    .active(true)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build());

            log.info("Default vision models seeded successfully");
        }
    }
}
