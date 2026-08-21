package com.luna.aggarly.property.entity;

import com.luna.aggarly.property.entity.enums.FeatureCategory;
import com.luna.aggarly.property.entity.enums.FeatureSource;
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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "property_image_features")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyImageFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tour_info_id", nullable = false)
    private PropertyImageTourInfo tourInfo;

    @Column(name = "feature_name", nullable = false, length = 100)
    private String featureName;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_category", nullable = false, length = 30)
    private FeatureCategory featureCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private FeatureSource source;

    @Column(name = "confidence_score")
    private Double confidenceScore;
}