package com.luna.aggarly.vision.search;

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

    @Value("${aggarly.vision.models.embedding.dimension:768}")
    private int vectorDim = 768;

    public record QueryVectors(
            float[] textVector,
            float[] imageVector
    ) {}

    public QueryVectors embed(VisionSearchQuery query) {
        log.debug("Embedding query in mode: {}", query.searchMode());

        CompletableFuture<float[]> textFuture = CompletableFuture.supplyAsync(() -> {
            if (query.rawText() != null && !query.rawText().isBlank()) {
                return embeddingService.embedText(query.rawText());
            }
            return new float[vectorDim];
        });

        CompletableFuture<float[]> imageFuture = CompletableFuture.supplyAsync(() -> {
            if (query.referenceImageBytes() != null && query.referenceImageBytes().length > 0) {
                return embeddingService.embedImage(query.referenceImageBytes(), null);
            }
            return new float[vectorDim];
        });

        CompletableFuture.allOf(textFuture, imageFuture).join();

        try {
            float[] textVec = textFuture.get();
            float[] imageVec = imageFuture.get();

            // If text-only mode, image vector mirrors text vector
            if (query.searchMode() == SearchMode.TEXT_ONLY) {
                imageVec = textVec;
            } else if (query.searchMode() == SearchMode.IMAGE_ONLY) {
                textVec = imageVec;
            }

            return new QueryVectors(textVec, imageVec);
        } catch (Exception e) {
            log.error("Error computing query vectors: {}", e.getMessage(), e);
            return new QueryVectors(new float[vectorDim], new float[vectorDim]);
        }
    }
}
