package com.luna.aggarly.vision.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.vision.exception.InvalidSearchImageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Base64;

/**
 * Validates whether an uploaded reference image belongs to the Real Estate / Property domain
 * and extracts semantic scene descriptions to power cross-modal reverse image search.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageDomainValidator {

    private final OllamaVisionClient ollamaVisionClient;
    private final ObjectMapper objectMapper;

    public record DomainValidationResult(
            boolean isPropertyDomain,
            String category,
            String detectedSubject,
            String sceneDescription,
            double confidence,
            String rejectionReason
    ) {
        public static DomainValidationResult valid(String subject, String description) {
            return new DomainValidationResult(true, "REAL_ESTATE", subject != null ? subject : "property / room scene", description, 1.0, null);
        }

        public static DomainValidationResult invalid(String category, String detectedSubject, String reason) {
            return new DomainValidationResult(false, category, detectedSubject, null, 0.95, reason);
        }
    }

    public DomainValidationResult validate(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return DomainValidationResult.valid("room", null);
        }

        if (ollamaVisionClient != null && ollamaVisionClient.isAvailable()) {
            try {
                String base64 = Base64.getEncoder().encodeToString(imageBytes);
                var responseOpt = ollamaVisionClient.chatWithVision(
                        base64,
                        VisionPromptTemplates.DOMAIN_VALIDATION_PROMPT,
                        "Verify if this photo is an in-domain rental property, room, architectural space, or landscape view, and describe the scene.",
                        null
                );

                if (responseOpt.isPresent()) {
                    String raw = responseOpt.get();
                    int first = raw.indexOf('{');
                    int last = raw.lastIndexOf('}');
                    if (first >= 0 && last > first) {
                        JsonNode node = objectMapper.readTree(raw.substring(first, last + 1));
                        boolean isDomain = node.path("isPropertyDomain").asBoolean(true);
                        String category = node.path("category").asText("REAL_ESTATE");
                        String subject = node.path("detectedSubject").asText("property scene");
                        String desc = node.path("sceneDescription").asText(node.path("detectedSubject").asText(""));
                        double conf = node.path("confidence").asDouble(0.95);
                        String reason = node.path("rejectionReason").asText(null);

                        if (!isDomain || !"REAL_ESTATE".equalsIgnoreCase(category)) {
                            log.warn("Out-of-domain search image rejected: category={}, subject={}, reason={}", category, subject, reason);
                            return new DomainValidationResult(false, category, subject, null, conf, reason);
                        }

                        return new DomainValidationResult(true, category, subject, desc, conf, null);
                    }
                }
            } catch (Exception e) {
                log.warn("Error during vision domain validation: {}, allowing search to proceed", e.getMessage());
            }
        }

        return DomainValidationResult.valid("property scene", null);
    }

    public DomainValidationResult validateOrThrow(byte[] imageBytes) {
        DomainValidationResult result = validate(imageBytes);
        if (!result.isPropertyDomain()) {
            throw new InvalidSearchImageException(result.detectedSubject(), result.rejectionReason());
        }
        return result;
    }
}
