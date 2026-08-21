package com.luna.aggarly.vision.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "vision_evaluation_runs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisionEvaluationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "pipeline_version", nullable = false, length = 20)
    private String pipelineVersion;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    @Column(name = "ndcg_at5")
    private Double ndcgAt5;

    @Column(name = "ndcg_at10")
    private Double ndcgAt10;

    @Column(name = "recall_at5")
    private Double recallAt5;

    @Column(name = "recall_at10")
    private Double recallAt10;

    @Column(name = "precision_at5")
    private Double precisionAt5;

    @Column(name = "mrr")
    private Double mrr;

    @Column(name = "filter_correctness")
    private Double filterCorrectness;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "full_report_json", columnDefinition = "jsonb")
    private String fullReportJson;

    @Column
    private Integer totalQueries;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
