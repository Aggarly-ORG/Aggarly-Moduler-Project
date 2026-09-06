package com.luna.aggarly.vision.dto;

import com.luna.aggarly.vision.entity.enums.ImageQualityGrade;
import com.luna.aggarly.vision.entity.enums.SceneType;
import com.luna.aggarly.vision.entity.enums.ViewType;

import java.util.List;
import java.util.UUID;

public record PhotoTourSceneDto(
        UUID imageId,
        UUID propertyId,
        String imageUrl,
        String storageKey,
        SceneType sceneType,
        String roomClusterId,
        String roomName,
        int sequenceIndex,
        Double qualityScore,
        ImageQualityGrade qualityGrade,
        ViewType viewType,
        String caption,
        List<String> verifiedAmenities,
        List<String> styleTags
) {}
