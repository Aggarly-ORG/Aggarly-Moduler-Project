package com.luna.aggarly.vision.search.records;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Encapsulates the exact property ID ranking produced at every individual search pipeline stage
 * alongside measured real execution durations in milliseconds.
 */
public record StageRankingDiagnostics(
        List<UUID> imageChannelRanking,
        List<UUID> captionChannelRanking,
        List<UUID> descriptionChannelRanking,
        List<UUID> fusedRanking,
        List<UUID> aggregatedRanking,
        List<UUID> mlRerankedRanking,
        List<UUID> finalRanking,
        Map<String, Long> stageLatenciesMs
) {}
