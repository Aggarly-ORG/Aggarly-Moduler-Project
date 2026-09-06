package com.luna.aggarly.vision.search;

import com.luna.aggarly.vision.pipeline.ImageDomainValidator;
import com.luna.aggarly.vision.search.records.SearchMode;
import com.luna.aggarly.vision.search.records.VisionSearchQuery;
import com.luna.aggarly.vision.vector.MultimodalEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class VisionQueryEmbedder {

    private final MultimodalEmbeddingService embeddingService;
    private final ImageDomainValidator imageDomainValidator;

    @Value("${aggarly.vision.models.embedding.dimension:768}")
    private int vectorDim = 768;

    @Value("${aggarly.vision.search.validate-image-domain:false}")
    private boolean validateImageDomain = false;

    public record QueryVectors(
            float[] textVector,
            float[] imageVector
    ) {}

    public QueryVectors embed(VisionSearchQuery query) {
        log.debug("Embedding query in mode: {}", query.searchMode());
        boolean hasImage = query.referenceImageBytes() != null && query.referenceImageBytes().length > 0;
        boolean hasText = query.rawText() != null && !query.rawText().isBlank();
        ImageDomainValidator.DomainValidationResult validationResult = null;
        if (hasImage && validateImageDomain && !query.skipDomainValidation()) {
            validationResult = imageDomainValidator.validateOrThrow(query.referenceImageBytes());
        }
        final ImageDomainValidator.DomainValidationResult finalValidation = validationResult;
        CompletableFuture<float[]> textFuture = CompletableFuture.supplyAsync(() -> {
            if (hasText) {
                return embeddingService.embedText(query.rawText());
            } else if (finalValidation != null && finalValidation.sceneDescription() != null && !finalValidation.sceneDescription().isBlank()) {
                log.info("Embedding extracted query image visual caption: '{}'", finalValidation.sceneDescription());
                return embeddingService.embedText(finalValidation.sceneDescription());
            }
            return new float[vectorDim];
        });

        CompletableFuture<float[]> imageFuture = CompletableFuture.supplyAsync(() -> {
            if (hasImage) {
                return embeddingService.embedImage(query.referenceImageBytes(), null);
            }
            return new float[vectorDim];
        });

        CompletableFuture.allOf(textFuture, imageFuture).join();

        try {
            float[] textVec = textFuture.get();
            float[] imageVec = imageFuture.get();
            if (query.searchMode() == SearchMode.TEXT_ONLY) {
                imageVec = textVec;
            } else if (query.searchMode() == SearchMode.IMAGE_ONLY) {
                if (isZeroVector(textVec)) {
                    textVec = imageVec;
                }
            }
            return new QueryVectors(textVec, imageVec);
        } catch (Exception e) {
            log.error("Error computing query vectors: {}", e.getMessage(), e);
            return new QueryVectors(new float[vectorDim], new float[vectorDim]);
        }
    }

    private boolean isZeroVector(float[] vec) {
        if (vec == null) return true;
        for (float v : vec) {
            if (v != 0.0f) return false;
        }
        return true;
    }
}
