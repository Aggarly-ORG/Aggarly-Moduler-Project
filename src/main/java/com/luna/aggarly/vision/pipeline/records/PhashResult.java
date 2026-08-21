package com.luna.aggarly.vision.pipeline.records;

import com.luna.aggarly.vision.entity.enums.DuplicateClassification;

import java.util.UUID;

public record PhashResult(
        String hash,
        DuplicateClassification duplicateClassification,
        UUID duplicateOfImageId
) {
    public boolean isExactDuplicate() {
        return duplicateClassification == DuplicateClassification.EXACT_DUPLICATE;
    }
}
