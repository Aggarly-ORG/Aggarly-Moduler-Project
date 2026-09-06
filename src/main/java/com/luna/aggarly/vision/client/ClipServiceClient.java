package com.luna.aggarly.vision.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * High-performance HTTP client communicating with the Python OpenCLIP Neural Embedding Microservice.
 */
@Slf4j
@Component
public class ClipServiceClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private String baseUrl = "http://127.0.0.1:8000";
    private long timeoutMs = 15000L;

    @Autowired
    public ClipServiceClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public ClipServiceClient(ObjectMapper objectMapper, String baseUrl, long timeoutMs) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl != null ? baseUrl : "http://127.0.0.1:8000";
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 15000L;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Value("${aggarly.vision.clip-service.url:http://127.0.0.1:8000}")
    public void setBaseUrl(String baseUrl) {
        if (baseUrl != null && !baseUrl.isBlank()) {
            this.baseUrl = baseUrl;
        }
    }

    @Value("${aggarly.vision.clip-service.timeout-ms:15000}")
    public void setTimeoutMs(long timeoutMs) {
        if (timeoutMs > 0) {
            this.timeoutMs = timeoutMs;
        }
    }

    /**
     * Checks whether the Python CLIP Microservice is available.
     */
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/health"))
                    .timeout(Duration.ofSeconds(3))
                    .version(HttpClient.Version.HTTP_1_1)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.debug("CLIP service health check failed at {}: {}", baseUrl, e.getMessage());
            return false;
        }
    }

    private volatile String cachedModelName = "ViT-B-32";

    /**
     * Queries or returns the active model name from the Python microservice.
     */
    public String getActiveModelName() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/health"))
                    .timeout(Duration.ofSeconds(2))
                    .version(HttpClient.Version.HTTP_1_1)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.has("model")) {
                    cachedModelName = root.get("model").asText("ViT-B-32");
                }
            }
        } catch (Exception ignored) {}
        return cachedModelName;
    }

    /**
     * Embeds an image by calling Python CLIP service.
     */
    public Optional<float[]> embedImage(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            log.warn("CLIP embedImage called with empty byte array");
            return Optional.empty();
        }

        try {
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            Map<String, String> requestBody = Map.of("image_base64", base64Image);
            String jsonPayload = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/embed/image-base64"))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .version(HttpClient.Version.HTTP_1_1)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.has("vector") && root.get("vector").isArray()) {
                    JsonNode vecNode = root.get("vector");
                    float[] result = new float[vecNode.size()];
                    for (int i = 0; i < vecNode.size(); i++) {
                        result[i] = (float) vecNode.get(i).asDouble();
                    }
                    log.info("Successfully generated OpenCLIP image embedding (dim={})", result.length);
                    return Optional.of(result);
                }
            } else {
                log.warn("CLIP image embedding failed: status={}, body={}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("Failed to call CLIP service for image embedding at {}: {}", baseUrl, e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Embeds a text query by calling Python CLIP service into the exact same latent space.
     */
    public Optional<float[]> embedText(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        try {
            Map<String, String> requestBody = Map.of("text", text.trim());
            String jsonPayload = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/embed/text"))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .version(HttpClient.Version.HTTP_1_1)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.has("vector") && root.get("vector").isArray()) {
                    JsonNode vecNode = root.get("vector");
                    float[] result = new float[vecNode.size()];
                    for (int i = 0; i < vecNode.size(); i++) {
                        result[i] = (float) vecNode.get(i).asDouble();
                    }
                    log.info("Successfully generated OpenCLIP text embedding (dim={})", result.length);
                    return Optional.of(result);
                }
            } else {
                log.warn("CLIP text embedding failed: status={}, body={}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("Failed to call CLIP service for text embedding at {}: {}", baseUrl, e.getMessage());
        }

        return Optional.empty();
    }
}
