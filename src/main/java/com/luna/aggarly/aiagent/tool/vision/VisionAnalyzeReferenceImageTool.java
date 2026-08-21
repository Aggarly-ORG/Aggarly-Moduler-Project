package com.luna.aggarly.aiagent.tool.vision;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.filestorage.service.FileStorageService;
import com.luna.aggarly.user.security.UserPrincipal;
import com.luna.aggarly.vision.pipeline.ConceptNormalizer;
import com.luna.aggarly.vision.pipeline.ImagePreprocessor;
import com.luna.aggarly.vision.pipeline.VisionInferenceEngine;
import com.luna.aggarly.vision.pipeline.records.NormalizedVisionResult;
import com.luna.aggarly.vision.pipeline.records.PreprocessedImage;
import com.luna.aggarly.vision.pipeline.records.VisionInferenceResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class VisionAnalyzeReferenceImageTool implements Tool<VisionAnalyzeReferenceImageTool.Params, VisionAnalyzeReferenceImageTool.ReferenceAnalysisResponse> {

    private final ImagePreprocessor preprocessor;
    private final VisionInferenceEngine inferenceEngine;
    private final ConceptNormalizer conceptNormalizer;
    private final FileStorageService fileStorageService;

    public record Params(
            String referenceObjectKey
    ) {}

    public record ReferenceAnalysisResponse(
            String sceneType,
            Double sceneConfidence,
            Boolean isIndoor,
            String viewType,
            String aiCaption,
            List<String> detectedObjects,
            List<String> styleTags,
            List<String> dominantColors,
            String naturalDescription
    ) {}

    @Override
    public String name() {
        return "vision.analyzeReferenceImage";
    }

    @Override
    public String description() {
        return "Analyze what is inside an uploaded reference image (scene type, objects, style, colors, features) without searching for properties.";
    }

    @Override
    public Class<Params> parameterType() {
        return Params.class;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ToolResult<ReferenceAnalysisResponse> execute(Params params, UserPrincipal currentUser) {
        log.info("Executing tool vision.analyzeReferenceImage with key: {}", params.referenceObjectKey());
        byte[] bytes = new byte[0];
        try (InputStream is = fileStorageService.getFileStream(params.referenceObjectKey())) {
            if (is != null) bytes = is.readAllBytes();
        } catch (Exception e) {
            log.warn("Could not read reference image stream: {}", e.getMessage());
        }

        UUID dummyImageId = UUID.randomUUID();
        UUID dummyPropertyId = UUID.randomUUID();

        PreprocessedImage preprocessed = preprocessor.preprocessBytes(dummyImageId, dummyPropertyId, bytes, params.referenceObjectKey());
        VisionInferenceResult inference = inferenceEngine.infer(preprocessed, null);
        NormalizedVisionResult normalized = conceptNormalizer.normalize(inference);

        List<String> objects = normalized.filteredObjects().stream().map(o -> o.objectName()).toList();
        List<String> styles = normalized.normalizedStyleTags().stream().map(s -> s.tag()).toList();

        ReferenceAnalysisResponse response = new ReferenceAnalysisResponse(
                normalized.raw().sceneType(),
                normalized.raw().sceneConfidence(),
                normalized.raw().isIndoor(),
                normalized.raw().viewType(),
                normalized.raw().aiCaption(),
                objects,
                styles,
                normalized.raw().dominantColors(),
                normalized.raw().aiCaption() != null ? normalized.raw().aiCaption() : "Interior view"
        );

        return ToolResult.success(response);
    }
}
