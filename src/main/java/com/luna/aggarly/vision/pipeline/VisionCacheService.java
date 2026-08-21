package com.luna.aggarly.vision.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.vision.pipeline.records.VisionInferenceResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VisionCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${aggarly.vision.cache.inference-ttl-days:7}")
    private int inferenceTtlDays = 7;

    @Value("${aggarly.vision.cache.embedding-ttl-days:30}")
    private int embeddingTtlDays = 30;

    private static final String INFERENCE_KEY_PREFIX = "vision:inference:";
    private static final String EMBEDDING_KEY_PREFIX = "vision:embed:image:";

    public Optional<VisionInferenceResult> getCachedInference(String pHash) {
        if (pHash == null || pHash.isBlank()) {
            return Optional.empty();
        }
        try {
            String cachedJson = redisTemplate.opsForValue().get(INFERENCE_KEY_PREFIX + pHash);
            if (cachedJson != null) {
                log.debug("Cache hit for vision inference on pHash: {}", pHash);
                return Optional.of(objectMapper.readValue(cachedJson, VisionInferenceResult.class));
            }
        } catch (Exception e) {
            log.warn("Redis cache read error for pHash {}: {}", pHash, e.getMessage());
        }
        return Optional.empty();
    }

    public void cacheInference(String pHash, VisionInferenceResult result) {
        if (pHash == null || pHash.isBlank() || result == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(INFERENCE_KEY_PREFIX + pHash, json, Duration.ofDays(inferenceTtlDays));
        } catch (Exception e) {
            log.warn("Redis cache write error for pHash {}: {}", pHash, e.getMessage());
        }
    }

    public Optional<float[]> getCachedImageEmbedding(String pHash) {
        if (pHash == null || pHash.isBlank()) {
            return Optional.empty();
        }
        try {
            String cachedJson = redisTemplate.opsForValue().get(EMBEDDING_KEY_PREFIX + pHash);
            if (cachedJson != null) {
                return Optional.of(objectMapper.readValue(cachedJson, float[].class));
            }
        } catch (Exception e) {
            log.warn("Redis cache read error for embedding pHash {}: {}", pHash, e.getMessage());
        }
        return Optional.empty();
    }

    public void cacheImageEmbedding(String pHash, float[] embedding) {
        if (pHash == null || pHash.isBlank() || embedding == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(embedding);
            redisTemplate.opsForValue().set(EMBEDDING_KEY_PREFIX + pHash, json, Duration.ofDays(embeddingTtlDays));
        } catch (Exception e) {
            log.warn("Redis cache write error for embedding pHash {}: {}", pHash, e.getMessage());
        }
    }
}
