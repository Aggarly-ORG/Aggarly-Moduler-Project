package com.luna.aggarly.vision.search.records;

public record VisionSearchQuery(
        String rawText,
        byte[] referenceImageBytes,
        String referenceObjectKey,
        float imageWeight,
        SearchMode searchMode,
        int pageSize,
        String cursor,
        float minScore,
        VisionSearchFilters hardFilters
) {
    public static VisionSearchQuery textQuery(String text, VisionSearchFilters filters, int pageSize, String cursor) {
        return textQuery(text, filters, pageSize, cursor, 0.20f);
    }

    public static VisionSearchQuery textQuery(String text, VisionSearchFilters filters, int pageSize, String cursor, Float minScore) {
        return new VisionSearchQuery(
                text, null, null, 0.0f, SearchMode.TEXT_ONLY,
                pageSize > 0 ? pageSize : 10, cursor,
                (minScore != null && minScore > 0) ? minScore : 0.20f,
                filters != null ? filters : VisionSearchFilters.empty()
        );
    }
}
