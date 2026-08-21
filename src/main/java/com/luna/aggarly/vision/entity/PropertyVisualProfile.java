package com.luna.aggarly.vision.entity;

import com.luna.aggarly.property.entity.Property;
import com.luna.aggarly.vision.entity.enums.ProfileStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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
@Table(name = "property_visual_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyVisualProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false, unique = true)
    private Property property;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_status", nullable = false, length = 30)
    @Builder.Default
    private ProfileStatus profileStatus = ProfileStatus.INCOMPLETE;

    @Column(name = "pipeline_version", nullable = false, length = 20)
    @Builder.Default
    private String pipelineVersion = "1.0.0";

    @Column(name = "total_images", nullable = false)
    @Builder.Default
    private int totalImages = 0;

    @Column(name = "processed_images", nullable = false)
    @Builder.Default
    private int processedImages = 0;

    @Column(name = "usable_images", nullable = false)
    @Builder.Default
    private int usableImages = 0;

    @Column(name = "coverage_score")
    @Builder.Default
    private Double coverageScore = 0.0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missing_key_rooms_json", columnDefinition = "jsonb")
    private String missingKeyRoomsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "room_coverage_json", columnDefinition = "jsonb")
    private String roomCoverageJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "best_per_scene_json", columnDefinition = "jsonb")
    private String bestPerSceneJson;

    @Column(name = "recommended_cover_image_id")
    private UUID recommendedCoverImageId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "representative_image_ids_json", columnDefinition = "jsonb")
    private String representativeImageIdsJson;

    @Column(name = "romantic_score")
    @Builder.Default
    private Double romanticScore = 0.0;

    @Column(name = "family_score")
    @Builder.Default
    private Double familyScore = 0.0;

    @Column(name = "luxury_score")
    @Builder.Default
    private Double luxuryScore = 0.0;

    @Column(name = "business_score")
    @Builder.Default
    private Double businessScore = 0.0;

    @Column(name = "relaxation_score")
    @Builder.Default
    private Double relaxationScore = 0.0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "aggregated_style_tags_json", columnDefinition = "jsonb")
    private String aggregatedStyleTagsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "aggregated_amenities_json", columnDefinition = "jsonb")
    private String aggregatedAmenitiesJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dominant_palette_json", columnDefinition = "jsonb")
    private String dominantPaletteJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "visual_fit_json", columnDefinition = "jsonb")
    private String visualFitJson;

    @Column(name = "visual_summary", columnDefinition = "TEXT")
    private String visualSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "image_coverage_report_json", columnDefinition = "jsonb")
    private String imageCoverageReportJson;

    @Column(name = "qdrant_point_id")
    private UUID qdrantPointId;

    @Column(name = "qdrant_collection", length = 100)
    private String qdrantCollection;

    @Column(name = "embedding_model_id")
    private UUID embeddingModelId;

    @Column(name = "embedded_at")
    private Instant embeddedAt;

    @Column(name = "last_aggregated_at")
    private Instant lastAggregatedAt;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
