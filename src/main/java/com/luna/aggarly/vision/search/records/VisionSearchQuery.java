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
        VisionSearchFilters hardFilters,
        boolean skipDomainValidation
) {
    public VisionSearchQuery(
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
        this(rawText, referenceImageBytes, referenceObjectKey, imageWeight, searchMode, pageSize, cursor, minScore, hardFilters, false);
    }

    public static VisionSearchQuery textQuery(String text, VisionSearchFilters filters, int pageSize, String cursor) {
        return textQuery(text, filters, pageSize, cursor, 0.20f);
    }

    public static VisionSearchQuery textQuery(String text, VisionSearchFilters filters, int pageSize, String cursor, Float minScore) {
        return new VisionSearchQuery(
                text, null, null, 0.0f, SearchMode.TEXT_ONLY,
                pageSize > 0 ? pageSize : 10, cursor,
                (minScore != null && minScore > 0) ? minScore : 0.20f,
                filters != null ? filters : VisionSearchFilters.empty(),
                false
        );
    }

    public static VisionSearchQuery imageQuery(byte[] imageBytes, String objectKey, VisionSearchFilters filters, int pageSize, String cursor, Float minScore) {
        return imageQuery(imageBytes, objectKey, filters, pageSize, cursor, minScore, false);
    }

    public static VisionSearchQuery imageQuery(byte[] imageBytes, String objectKey, VisionSearchFilters filters, int pageSize, String cursor, Float minScore, boolean skipDomainValidation) {
        return new VisionSearchQuery(
                "", imageBytes, objectKey, 1.0f, SearchMode.IMAGE_ONLY,
                pageSize > 0 ? pageSize : 10, cursor,
                (minScore != null && minScore > 0) ? minScore : 0.20f,
                filters != null ? filters : VisionSearchFilters.empty(),
                skipDomainValidation
        );
    }

    public static VisionSearchQuery multimodalQuery(byte[] imageBytes, String objectKey, String text, float imageWeight, VisionSearchFilters filters, int pageSize, String cursor, Float minScore) {
        return multimodalQuery(imageBytes, objectKey, text, imageWeight, filters, pageSize, cursor, minScore, false);
    }

    public static VisionSearchQuery multimodalQuery(byte[] imageBytes, String objectKey, String text, float imageWeight, VisionSearchFilters filters, int pageSize, String cursor, Float minScore, boolean skipDomainValidation) {
        return new VisionSearchQuery(
                text != null ? text : "", imageBytes, objectKey, imageWeight > 0 ? imageWeight : 0.60f, SearchMode.MULTIMODAL,
                pageSize > 0 ? pageSize : 10, cursor,
                (minScore != null && minScore > 0) ? minScore : 0.20f,
                filters != null ? filters : VisionSearchFilters.empty(),
                skipDomainValidation
        );
    }
}
