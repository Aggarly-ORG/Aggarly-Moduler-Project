package com.luna.aggarly.vision.entity;

import com.luna.aggarly.property.entity.Property;
import com.luna.aggarly.property.entity.PropertyImage;
import com.luna.aggarly.vision.entity.enums.DuplicateClassification;
import com.luna.aggarly.vision.entity.enums.ImageQualityGrade;
import com.luna.aggarly.vision.entity.enums.ModerationStatus;
import com.luna.aggarly.vision.entity.enums.SceneType;
import com.luna.aggarly.vision.entity.enums.ViewType;
import com.luna.aggarly.vision.entity.enums.VisionIndexState;
import com.luna.aggarly.vision.entity.enums.VisionProcessingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "property_image_ai_metadata")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyImageAiMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_image_id", nullable = false, unique = true)
    private PropertyImage propertyImage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Column(name = "pipeline_version", nullable = false, length = 20)
    @Builder.Default
    private String pipelineVersion = "1.0.0";

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 30)
    @Builder.Default
    private VisionProcessingStatus processingStatus = VisionProcessingStatus.PENDING;

    @Column(name = "current_stage", length = 50)
    private String currentStage;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "classification_model_id")
    private UUID classificationModelId;

    @Column(name = "embedding_model_id")
    private UUID embeddingModelId;

    @Column(name = "prompt_version", length = 20)
    private String promptVersion;

    @Column(name = "preprocessing_version", length = 20)
    private String preprocessingVersion;

    @Column(name = "last_processed_at")
    private Instant lastProcessedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "vector_index_state", nullable = false, length = 20)
    @Builder.Default
    private VisionIndexState vectorIndexState = VisionIndexState.NOT_INDEXED;

    @Column(name = "perceptual_hash", length = 64)
    private String perceptualHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "duplicate_classification", nullable = false, length = 20)
    @Builder.Default
    private DuplicateClassification duplicateClassification = DuplicateClassification.UNIQUE;

    @Column(name = "duplicate_of_image_id")
    private UUID duplicateOfImageId;

    @Column(name = "quality_score")
    private Double qualityScore;

    @Column(name = "technical_quality_score")
    private Double technicalQualityScore;

    @Column(name = "visual_usability_score")
    private Double visualUsabilityScore;

    @Column(name = "searchability_score")
    private Double searchabilityScore;

    @Column(name = "sharpness_score")
    private Double sharpnessScore;

    @Column(name = "brightness_score")
    private Double brightnessScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality_grade", length = 20)
    private ImageQualityGrade qualityGrade;

    @Column(name = "is_blurry")
    @Builder.Default
    private boolean blurry = false;

    @Column(name = "is_dark")
    @Builder.Default
    private boolean dark = false;

    @Column(name = "is_overexposed")
    @Builder.Default
    private boolean overexposed = false;

    @Column(name = "is_screenshot")
    @Builder.Default
    private boolean screenshot = false;

    @Column(name = "is_collage")
    @Builder.Default
    private boolean collage = false;

    @Column(name = "is_relevant_to_property")
    @Builder.Default
    private boolean relevantToProperty = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "scene_type", length = 50)
    private SceneType sceneType;

    @Column(name = "scene_confidence")
    private Double sceneConfidence;

    @Column(name = "is_indoor")
    private Boolean indoor;

    @Enumerated(EnumType.STRING)
    @Column(name = "view_type", length = 50)
    private ViewType viewType;

    @Column(name = "room_cluster_id", length = 64)
    private String roomClusterId;

    @Column(name = "ai_caption", length = 1000)
    private String aiCaption;

    @Column(name = "alt_text", length = 500)
    private String altText;

    @Column(name = "host_caption", length = 500)
    private String hostCaption;

    @Column(name = "ocr_text", columnDefinition = "TEXT")
    private String ocrText;

    @Column(name = "visual_summary", columnDefinition = "TEXT")
    private String visualSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detected_objects_json", columnDefinition = "jsonb")
    private String detectedObjectsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detected_amenities_json", columnDefinition = "jsonb")
    private String detectedAmenitiesJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "style_tags_json", columnDefinition = "jsonb")
    private String styleTagsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dominant_colors_json", columnDefinition = "jsonb")
    private String dominantColorsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "condition_assessment_json", columnDefinition = "jsonb")
    private String conditionAssessmentJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "visual_features_json", columnDefinition = "jsonb")
    private String visualFeaturesJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "visual_fit_json", columnDefinition = "jsonb")
    private String visualFitJson;

    @Column(name = "qdrant_point_id")
    private UUID qdrantPointId;

    @Column(name = "qdrant_collection", length = 100)
    private String qdrantCollection;

    @Column(name = "embedded_at")
    private Instant embeddedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 30)
    @Builder.Default
    private ModerationStatus moderationStatus = ModerationStatus.APPROVED;

    @Column(name = "moderation_reason", length = 300)
    private String moderationReason;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
