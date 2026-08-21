package com.luna.aggarly.aiagent.engine;

public interface EmbeddingClient {
    float[] generateImageEmbedding(String imageKeyOrUrl);
}
