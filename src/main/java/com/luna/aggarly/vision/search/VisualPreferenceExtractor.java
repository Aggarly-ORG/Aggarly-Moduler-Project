package com.luna.aggarly.vision.search;

import com.luna.aggarly.filestorage.service.FileStorageService;
import com.luna.aggarly.vision.pipeline.ConceptNormalizer;
import com.luna.aggarly.vision.pipeline.ImagePreprocessor;
import com.luna.aggarly.vision.pipeline.VisionInferenceEngine;
import com.luna.aggarly.vision.pipeline.records.NormalizedVisionResult;
import com.luna.aggarly.vision.pipeline.records.PreprocessedImage;
import com.luna.aggarly.vision.pipeline.records.VisionInferenceResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VisualPreferenceExtractor {

    private final ImagePreprocessor preprocessor;
    private final VisionInferenceEngine inferenceEngine;
    private final ConceptNormalizer conceptNormalizer;
    private final FileStorageService fileStorageService;

    public record ExtractedPreferences(
            String dominantSceneType,
            List<String> preferredStyles,
            List<String> desiredAmenities,
            List<String> dominantColors,
            Map<String, Double> lifestyleFitScores,
            String naturalLanguageSummary
    ) {}

    public ExtractedPreferences extractPreferencesFromImage(String referenceObjectKey, UUID userId) {
        log.info("Extracting user visual preferences from image: objectKey={}, userId={}", referenceObjectKey, userId);
        byte[] bytes = new byte[0];
        try (InputStream is = fileStorageService.getFileStream(referenceObjectKey)) {
            if (is != null) bytes = is.readAllBytes();
        } catch (Exception e) {
            log.warn("Failed to load reference image bytes: {}", e.getMessage());
        }

        UUID dummyImageId = UUID.randomUUID();
        UUID dummyPropertyId = UUID.randomUUID();

        PreprocessedImage preprocessed = preprocessor.preprocessBytes(dummyImageId, dummyPropertyId, bytes, referenceObjectKey);
        VisionInferenceResult inference = inferenceEngine.infer(preprocessed, null);
        NormalizedVisionResult normalized = conceptNormalizer.normalize(inference);

        List<String> styles = normalized.normalizedStyleTags().stream().map(s -> s.tag()).toList();
        List<String> amenities = normalized.normalizedAmenities().stream().map(a -> a.amenityName()).toList();

        String summary = String.format("User prefers %s aesthetic with %s style and features like %s.",
                normalized.raw().sceneType(),
                String.join(", ", styles),
                String.join(", ", amenities)
        );

        return new ExtractedPreferences(
                normalized.raw().sceneType(),
                styles,
                amenities,
                normalized.raw().dominantColors(),
                normalized.visualFitScores(),
                summary
        );
    }
}
