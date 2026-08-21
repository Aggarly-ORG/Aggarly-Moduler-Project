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
@Table(name = "vision_eval_queries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisionEvalQuery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "query_text", columnDefinition = "TEXT")
    private String queryText;

    @Column(name = "reference_image_key", length = 500)
    private String referenceImageKey;

    @Column(name = "query_type", nullable = false, length = 30)
    private String queryType;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "min_guests")
    private Integer minGuests;

    @Column(name = "max_price_per_night")
    private Double maxPricePerNight;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "expected_property_ids_json", columnDefinition = "jsonb")
    private String expectedPropertyIdsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "relevance_grades_json", columnDefinition = "jsonb")
    private String relevanceGradesJson;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
