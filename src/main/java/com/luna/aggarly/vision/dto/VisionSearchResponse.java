package com.luna.aggarly.vision.dto;

import com.luna.aggarly.vision.search.records.VisionSearchResult;

import java.util.List;

public record VisionSearchResponse(
        List<VisionSearchResult> results,
        int count,
        String nextCursor
) {}
