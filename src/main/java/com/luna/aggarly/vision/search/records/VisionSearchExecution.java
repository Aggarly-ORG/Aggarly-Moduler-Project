package com.luna.aggarly.vision.search.records;

import java.util.List;

/**
 * Result of a vision search execution bundled with detailed per-stage ranking diagnostics.
 */
public record VisionSearchExecution(
        List<VisionSearchResult> results,
        StageRankingDiagnostics diagnostics
) {}
