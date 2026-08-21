package com.luna.aggarly.vision.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class OllamaVisionClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${aggarly.vision.ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${aggarly.vision.models.perception.vlm:minimax-m3:cloud}")
    private String defaultVlmModel;

    @Value("${aggarly.vision.models.embedding.text-model:nomic-embed-text}")
    private String defaultEmbeddingModel;

    @Value("${aggarly.vision.models.perception.timeout-ms:45000}")
    private long vlmTimeoutMs;

    public OllamaVisionClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Check if Ollama server is reachable.
     */
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.debug("Ollama is not reachable at {}: {}", baseUrl, e.getMessage());
            return false;
        }
    }

    /**
     * List all installed models in Ollama.
     */
    public List<String> listModels() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.has("models") && root.get("models").isArray()) {
                    List<String> names = new ArrayList<>();
                    for (JsonNode modelNode : root.get("models")) {
                        if (modelNode.has("name")) {
                            names.add(modelNode.get("name").asText());
                        }
                    }
                    return names;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to list Ollama models: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private static final List<String> KNOWN_VISION_MODEL_PREFIXES = List.of(
            "llama3.2-vision", "llava", "moondream", "qwen2-vl", "qwen2.5-vl", "minicpm-v", "bakllava", "minimax-m3"
    );

    /**
     * Resolve the best available vision model installed in Ollama.
     */
    public String resolveVisionModel(String preferredModel) {
        List<String> installed = listModels();
        if (installed.isEmpty()) {
            return preferredModel != null && !preferredModel.isBlank() ? preferredModel : defaultVlmModel;
        }

        // 1. If preferredModel is directly installed, use it
        if (preferredModel != null && !preferredModel.isBlank()) {
            for (String m : installed) {
                if (m.equalsIgnoreCase(preferredModel) || m.startsWith(preferredModel + ":")) {
                    return m;
                }
            }
        }

        // 2. Check if defaultVlmModel is installed
        for (String m : installed) {
            if (m.equalsIgnoreCase(defaultVlmModel) || m.startsWith(defaultVlmModel + ":")) {
                return m;
            }
        }

        // 3. Find any installed model that matches a known vision architecture
        for (String m : installed) {
            String lower = m.toLowerCase();
            for (String prefix : KNOWN_VISION_MODEL_PREFIXES) {
                if (lower.contains(prefix)) {
                    log.info("Auto-selected installed vision model '{}' from Ollama", m);
                    return m;
                }
            }
        }

        // 4. Return first installed model or fallback
        log.warn("No dedicated vision model found in Ollama installed list: {}. Using {}", installed, defaultVlmModel);
        return installed.get(0);
    }

    /**
     * Run multimodal vision inference on an image (passed as Base64 JPEG) with a prompt.
     * Uses Ollama /api/chat with format="json".
     */
    public Optional<String> chatWithVision(String base64Image, String systemPrompt, String userPrompt, String modelOverride) {
        String model = resolveVisionModel(modelOverride != null && !modelOverride.isBlank() ? modelOverride : defaultVlmModel);

        try {
            Map<String, Object> messagePayload;
            if (base64Image != null && !base64Image.isBlank()) {
                messagePayload = Map.of(
                        "role", "user",
                        "content", userPrompt != null ? userPrompt : "Analyze this property photo in detail.",
                        "images", List.of(base64Image)
                );
            } else {
                messagePayload = Map.of(
                        "role", "user",
                        "content", userPrompt != null ? userPrompt : "Analyze this property photo in detail."
                );
            }

            List<Map<String, Object>> messages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                messages.add(Map.of("role", "system", "content", systemPrompt));
            }
            messages.add(messagePayload);

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", messages,
                    "stream", false,
                    "format", "json",
                    "options", Map.of(
                            "temperature", 0.1,
                            "num_predict", 1024
                    )
            );

            String jsonPayload = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/chat"))
                    .timeout(Duration.ofMillis(vlmTimeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            log.info("Sending multimodal vision request to Ollama ({}) for model {}", baseUrl, model);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.has("message") && root.get("message").has("content")) {
                    String content = root.get("message").get("content").asText();
                    return Optional.ofNullable(content);
                }
            } else {
                log.error("Ollama vision request failed [HTTP {}]: {} (Model: {})", response.statusCode(), response.body(), model);
            }
        } catch (Exception e) {
            log.error("Ollama vision inference call exception (model={}): {}", model, e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Generate text / multimodal embeddings from Ollama via /api/embed or /api/embeddings.
     */
    public Optional<float[]> getEmbedding(String text, String modelOverride) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        String model = (modelOverride != null && !modelOverride.isBlank()) ? modelOverride : defaultEmbeddingModel;

        try {
            // Try modern Ollama /api/embed endpoint first
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "input", text
            );

            String jsonPayload = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/embed"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.has("embeddings") && root.get("embeddings").isArray() && !root.get("embeddings").isEmpty()) {
                    JsonNode firstVec = root.get("embeddings").get(0);
                    if (firstVec.isArray()) {
                        float[] result = new float[firstVec.size()];
                        for (int i = 0; i < firstVec.size(); i++) {
                            result[i] = (float) firstVec.get(i).asDouble();
                        }
                        return Optional.of(result);
                    }
                }
            } else if (response.statusCode() == 404) {
                // Fallback to legacy /api/embeddings
                return getLegacyEmbedding(text, model);
            }
        } catch (Exception e) {
            log.debug("Ollama /api/embed failed, attempting legacy endpoint: {}", e.getMessage());
            return getLegacyEmbedding(text, model);
        }

        return Optional.empty();
    }

    private Optional<float[]> getLegacyEmbedding(String text, String model) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "prompt", text
            );

            String jsonPayload = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/embeddings"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.has("embedding") && root.get("embedding").isArray()) {
                    JsonNode vecNode = root.get("embedding");
                    float[] result = new float[vecNode.size()];
                    for (int i = 0; i < vecNode.size(); i++) {
                        result[i] = (float) vecNode.get(i).asDouble();
                    }
                    return Optional.of(result);
                }
            }
        } catch (Exception e) {
            log.debug("Ollama legacy /api/embeddings call failed: {}", e.getMessage());
        }
        return Optional.empty();
    }
}
