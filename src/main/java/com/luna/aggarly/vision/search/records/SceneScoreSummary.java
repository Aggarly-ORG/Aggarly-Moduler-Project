package com.luna.aggarly.vision.search.records;

import java.util.Map;
import java.util.UUID;

public record SceneScoreSummary(
        float bestBedroomScore,
        float bestBathroomScore,
        float bestLivingRoomScore,
        float bestExteriorScore,
        float bestPoolScore,
        float bestViewScore,
        Map<String, UUID> bestImagePerScene
) {}
