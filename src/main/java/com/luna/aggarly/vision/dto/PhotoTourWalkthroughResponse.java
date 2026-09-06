package com.luna.aggarly.vision.dto;

import java.util.List;
import java.util.UUID;

public record PhotoTourWalkthroughResponse(
        UUID propertyId,
        String propertyTitle,
        int totalScenes,
        List<PhotoTourSceneDto> scenes,
        List<String> highlightedAmenities,
        String visualSummary
) {}
