package com.luna.aggarly.vision.entity;

import com.luna.aggarly.vision.entity.enums.ModelModality;
import com.luna.aggarly.vision.entity.enums.VisionModelTaskType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "vision_model_registry")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisionModelRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "model_version", nullable = false, length = 50)
    private String modelVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 50)
    private VisionModelTaskType taskType;

    @Column(name = "embedding_dim")
    private Integer embeddingDim;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "modality", length = 30)
    private ModelModality modality;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "prompt_version", length = 20)
    private String promptVersion;

    @Column(name = "preprocessing_version", length = 20)
    private String preprocessingVersion;

    @Column(name = "model_revision", length = 100)
    private String modelRevision;

    @Column(name = "model_checksum", length = 64)
    private String modelChecksum;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuration_json", columnDefinition = "jsonb")
    private String configurationJson;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
