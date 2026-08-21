package com.luna.aggarly.aiagent.engine.impl;

import com.luna.aggarly.aiagent.engine.EmbeddingClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "aggarly.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockEmbeddingClientImpl implements EmbeddingClient {

    @Override
    public float[] generateImageEmbedding(String imageKeyOrUrl) {
        log.info("MockEmbeddingClient generating 512-dim CLIP embedding for image: {}", imageKeyOrUrl);
        float[] dummyEmbedding = new float[512];
        for (int i = 0; i < 512; i++) {
            dummyEmbedding[i] = (float) (Math.sin(i) * 0.1);
        }
        return dummyEmbedding;
    }
}
