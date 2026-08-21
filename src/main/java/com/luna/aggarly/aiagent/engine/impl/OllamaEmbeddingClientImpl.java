package com.luna.aggarly.aiagent.engine.impl;

import com.luna.aggarly.aiagent.engine.EmbeddingClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "aggarly.ai.provider", havingValue = "ollama")
public class OllamaEmbeddingClientImpl implements EmbeddingClient {

    private final EmbeddingModel embeddingModel;

    @Autowired
    public OllamaEmbeddingClientImpl(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
        log.info("Initialized OllamaEmbeddingClientImpl using Spring AI EmbeddingModel");
    }

    @Override
    public float[] generateImageEmbedding(String imageKeyOrUrl) {
        log.info("Spring AI EmbeddingModel generating vector embedding for: {}", imageKeyOrUrl);
        try {
            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(imageKeyOrUrl));
            if (response != null && response.getResults() != null && !response.getResults().isEmpty()) {
                return response.getResults().get(0).getOutput();
            }
        } catch (Exception ex) {
            log.warn("Spring AI EmbeddingModel generation failed: {}", ex.getMessage());
        }

        float[] dummyEmbedding = new float[512];
        for (int i = 0; i < 512; i++) {
            dummyEmbedding[i] = (float) (Math.sin(i) * 0.1);
        }
        return dummyEmbedding;
    }
}
