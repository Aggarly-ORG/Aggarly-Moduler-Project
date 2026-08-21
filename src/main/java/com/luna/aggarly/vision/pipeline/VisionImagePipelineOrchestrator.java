package com.luna.aggarly.vision.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.property.entity.Property;
import com.luna.aggarly.property.entity.PropertyImage;
import com.luna.aggarly.property.repository.PropertyImageRepository;
import com.luna.aggarly.property.repository.PropertyRepository;
import com.luna.aggarly.vision.entity.PropertyImageAiMetadata;
import com.luna.aggarly.vision.entity.enums.DuplicateClassification;
import com.luna.aggarly.vision.entity.enums.ImageQualityGrade;
import com.luna.aggarly.vision.entity.enums.ModerationStatus;
import com.luna.aggarly.vision.entity.enums.SceneType;
import com.luna.aggarly.vision.entity.enums.ViewType;
import com.luna.aggarly.vision.entity.enums.VisionIndexState;
import com.luna.aggarly.vision.entity.enums.VisionProcessingStatus;
import com.luna.aggarly.vision.event.PropertyImageAnalyzedEvent;
import com.luna.aggarly.vision.exception.VisionPipelineException;
import com.luna.aggarly.vision.pipeline.records.NormalizedVisionResult;
import com.luna.aggarly.vision.pipeline.records.PhashResult;
import com.luna.aggarly.vision.pipeline.records.PreprocessedImage;
import com.luna.aggarly.vision.pipeline.records.QualityAssessmentResult;
import com.luna.aggarly.vision.pipeline.records.VisionAnalysisOptions;
import com.luna.aggarly.vision.pipeline.records.VisionInferenceResult;
import com.luna.aggarly.vision.pipeline.records.VisionPipelineResult;
import com.luna.aggarly.vision.repository.PropertyImageAiMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VisionImagePipelineOrchestrator {

    private final ImagePreprocessor imagePreprocessor;
    private final PerceptualHashService perceptualHashService;
    private final ImageQualityAssessmentService qualityAssessmentService;
    private final VisionInferenceEngine inferenceEngine;
    private final ConceptNormalizer conceptNormalizer;
    private final com.luna.aggarly.vision.vector.MultimodalEmbeddingService embeddingService;
    private final com.luna.aggarly.vision.vector.QdrantVisionClient qdrantClient;
    private final PropertyImageAiMetadataRepository metadataRepository;
    private final PropertyImageRepository propertyImageRepository;
    private final PropertyRepository propertyRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public VisionPipelineResult processImage(UUID imageId, UUID propertyId, String objectKey, VisionAnalysisOptions options) {
        log.info("Starting Vision Pipeline for imageId={}, propertyId={}", imageId, propertyId);

        PropertyImage propertyImage = propertyImageRepository.findById(imageId)
                .orElseThrow(() -> new VisionPipelineException("PropertyImage entity not found for id: " + imageId));
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new VisionPipelineException("Property entity not found for id: " + propertyId));

        PropertyImageAiMetadata metadata = metadataRepository.findByPropertyImageId(imageId)
                .orElseGet(() -> PropertyImageAiMetadata.builder()
                        .propertyImage(propertyImage)
                        .property(property)
                        .processingStatus(VisionProcessingStatus.PROCESSING)
                        .build());

        metadata.setProcessingStatus(VisionProcessingStatus.PREPROCESSING);
        metadata.setCurrentStage("PREPROCESSING");
        metadata.setAttemptCount(metadata.getAttemptCount() + 1);

        try {
            // Stage 1: Preprocessing (Download, scale to 1024x1024, strip EXIF, normalize RGB)
            PreprocessedImage preprocessed = imagePreprocessor.preprocess(imageId, propertyId, objectKey);
            metadata.setCurrentStage("PHASH");

            // Stage 2: Perceptual Hashing & Duplicate Classification
            String pHash = perceptualHashService.computeDHash(preprocessed.bytes());
            PhashResult phashResult = perceptualHashService.checkDuplicate(propertyId, imageId, pHash);
            metadata.setPerceptualHash(pHash);
            metadata.setDuplicateClassification(phashResult.duplicateClassification());
            metadata.setDuplicateOfImageId(phashResult.duplicateOfImageId());

            // If exact duplicate (Hamming distance == 0), skip costly deep VLM inference
            if (phashResult.isExactDuplicate()) {
                log.info("Image {} is EXACT duplicate of {}. Marking SKIPPED.", imageId, phashResult.duplicateOfImageId());
                metadata.setProcessingStatus(VisionProcessingStatus.SKIPPED);
                metadata.setFailureReason("Exact duplicate of image: " + phashResult.duplicateOfImageId());
                metadata.setVectorIndexState(VisionIndexState.NOT_INDEXED);
                PropertyImageAiMetadata saved = metadataRepository.save(metadata);
                return new VisionPipelineResult(saved, false, "Image skipped: exact duplicate");
            }

            metadata.setCurrentStage("QUALITY_CHECK");

            // Stage 3: Image Quality Assessment (3 distinct dimensions)
            QualityAssessmentResult quality = qualityAssessmentService.assess(preprocessed.bytes());
            metadata.setQualityScore(quality.qualityScore());
            metadata.setTechnicalQualityScore(quality.technicalQualityScore());
            metadata.setVisualUsabilityScore(quality.visualUsabilityScore());
            metadata.setSearchabilityScore(quality.searchabilityScore());
            metadata.setSharpnessScore(quality.sharpnessScore());
            metadata.setBrightnessScore(quality.brightnessScore());
            metadata.setQualityGrade(quality.grade());
            metadata.setBlurry(quality.blurry());
            metadata.setDark(quality.dark());
            metadata.setOverexposed(quality.overexposed());
            metadata.setScreenshot(quality.screenshot());
            metadata.setCollage(quality.collage());

            // If image is permanently rejected (unusable), skip further processing
            if (!quality.passedQualityGate() || quality.grade() == ImageQualityGrade.REJECTED) {
                log.warn("Image {} REJECTED by quality gate (score={}). Marking SKIPPED.", imageId, quality.qualityScore());
                metadata.setProcessingStatus(VisionProcessingStatus.SKIPPED);
                metadata.setFailureReason("Rejected by quality gate: insufficient visual quality or invalid asset");
                metadata.setVectorIndexState(VisionIndexState.NOT_INDEXED);
                PropertyImageAiMetadata saved = metadataRepository.save(metadata);
                return new VisionPipelineResult(saved, false, "Image skipped: rejected by quality gate");
            }

            metadata.setCurrentStage("INFERRING");
            metadata.setProcessingStatus(VisionProcessingStatus.INFERRING);

            // Stage 4: Vision Model Inference (Scene, Captions, Objects, Amenities, Condition, OCR, Moderation)
            VisionInferenceResult inference = inferenceEngine.infer(preprocessed, pHash);

            metadata.setCurrentStage("NORMALIZING");
            metadata.setProcessingStatus(VisionProcessingStatus.NORMALIZING);

            // Stage 5: Concept Normalization & Provenance Assignment
            NormalizedVisionResult normalized = conceptNormalizer.normalize(inference);

            // Apply fields to Entity
            applyNormalizedResults(metadata, normalized);

            boolean requiresEmbedding = (metadata.getModerationStatus() == ModerationStatus.APPROVED) &&
                    (metadata.getQualityGrade() != ImageQualityGrade.REJECTED) &&
                    (!phashResult.isExactDuplicate());

            if (requiresEmbedding) {
                try {
                    metadata.setCurrentStage("EMBEDDING");
                    float[] imageVector = embeddingService.embedImage(preprocessed.bytes(), pHash);
                    float[] captionVector = embeddingService.embedCaption(metadata.getAiCaption(), metadata.getAltText(), metadata.getVisualSummary());

                    java.util.Map<String, float[]> namedVectors = java.util.Map.of(
                            "image_vector", imageVector,
                            "caption_vector", captionVector
                    );

                    java.util.Map<String, Object> payload = new java.util.HashMap<>();
                    payload.put("propertyId", propertyId.toString());
                    payload.put("imageId", imageId.toString());
                    payload.put("sceneType", metadata.getSceneType() != null ? metadata.getSceneType().name() : "BEDROOM");
                    payload.put("isIndoor", metadata.getIndoor() != null ? metadata.getIndoor() : true);
                    payload.put("viewType", metadata.getViewType() != null ? metadata.getViewType().name() : "NONE");
                    payload.put("isCover", propertyImage.isCover());
                    payload.put("qualityGrade", metadata.getQualityGrade() != null ? metadata.getQualityGrade().name() : "ACCEPTABLE");
                    payload.put("qualityScore", metadata.getQualityScore() != null ? metadata.getQualityScore() : 0.80);
                    payload.put("moderationStatus", metadata.getModerationStatus().name());

                    qdrantClient.upsertMultiVectorPoint("property_images_v1", imageId, namedVectors, payload);

                    metadata.setQdrantPointId(imageId);
                    metadata.setQdrantCollection("property_images_v1");
                    metadata.setEmbeddedAt(Instant.now());
                    metadata.setVectorIndexState(VisionIndexState.INDEXED);
                    log.info("Directly indexed image {} into Qdrant collection property_images_v1", imageId);
                } catch (Exception ex) {
                    log.warn("Direct Qdrant indexing failed for image {}: {}", imageId, ex.getMessage());
                    metadata.setVectorIndexState(VisionIndexState.FAILED);
                }
            } else {
                metadata.setVectorIndexState(VisionIndexState.NOT_INDEXED);
            }

            metadata.setCurrentStage("COMPLETED");
            metadata.setProcessingStatus(VisionProcessingStatus.COMPLETED);
            metadata.setLastProcessedAt(Instant.now());

            PropertyImageAiMetadata saved = metadataRepository.save(metadata);

            eventPublisher.publishEvent(new PropertyImageAnalyzedEvent(imageId, propertyId, objectKey, requiresEmbedding));

            log.info("Vision Pipeline completed successfully for imageId={}", imageId);
            return new VisionPipelineResult(saved, requiresEmbedding, "Pipeline completed successfully");
        } catch (Exception e) {
            log.error("Vision Pipeline failed for imageId={}: {}", imageId, e.getMessage(), e);
            metadata.setProcessingStatus(VisionProcessingStatus.FAILED);
            metadata.setFailureReason(e.getMessage());
            metadata.setVectorIndexState(VisionIndexState.FAILED);
            PropertyImageAiMetadata saved = metadataRepository.save(metadata);
            throw new VisionPipelineException("Pipeline error: " + e.getMessage(), e, null);
        }
    }

    private void applyNormalizedResults(PropertyImageAiMetadata metadata, NormalizedVisionResult normalized) {
        VisionInferenceResult raw = normalized.raw();

        try {
            if (raw.sceneType() != null) {
                metadata.setSceneType(SceneType.valueOf(raw.sceneType().toUpperCase()));
            }
        } catch (Exception e) {
            metadata.setSceneType(SceneType.BEDROOM);
        }

        metadata.setSceneConfidence(raw.sceneConfidence());
        metadata.setIndoor(raw.isIndoor());

        try {
            if (raw.viewType() != null) {
                metadata.setViewType(ViewType.valueOf(raw.viewType().toUpperCase()));
            }
        } catch (Exception e) {
            metadata.setViewType(ViewType.NONE);
        }

        metadata.setAiCaption(raw.aiCaption());
        metadata.setAltText(raw.altText());
        metadata.setOcrText(raw.ocrText());
        metadata.setRoomClusterId(normalized.roomClusterId());

        try {
            if (raw.moderationStatus() != null) {
                metadata.setModerationStatus(ModerationStatus.valueOf(raw.moderationStatus().toUpperCase()));
            }
        } catch (Exception e) {
            metadata.setModerationStatus(ModerationStatus.APPROVED);
        }
        metadata.setModerationReason(raw.moderationReason());
        metadata.setPromptVersion(raw.promptVersion());
        metadata.setPreprocessingVersion(raw.preprocessingVersion());

        // Serialize JSONB fields
        try {
            metadata.setDetectedObjectsJson(objectMapper.writeValueAsString(normalized.filteredObjects()));
            metadata.setDetectedAmenitiesJson(objectMapper.writeValueAsString(normalized.normalizedAmenities()));
            metadata.setStyleTagsJson(objectMapper.writeValueAsString(normalized.normalizedStyleTags()));
            metadata.setDominantColorsJson(objectMapper.writeValueAsString(raw.dominantColors()));
            metadata.setConditionAssessmentJson(objectMapper.writeValueAsString(raw.conditionAssessment()));
            metadata.setVisualFeaturesJson(objectMapper.writeValueAsString(raw.specialFeatures()));
            metadata.setVisualFitJson(objectMapper.writeValueAsString(normalized.visualFitScores()));

            // Build human-friendly visual summary string
            String summary = String.format("%s. Detected features: %s. Aesthetic tags: %s.",
                    raw.aiCaption() != null ? raw.aiCaption() : "Property view",
                    normalized.normalizedAmenities().stream().map(a -> a.amenityName()).toList(),
                    normalized.normalizedStyleTags().stream().map(s -> s.tag()).toList()
            );
            metadata.setVisualSummary(summary);
        } catch (Exception e) {
            log.warn("Error serializing JSONB metadata fields: {}", e.getMessage());
        }
    }
}
