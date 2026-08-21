package com.luna.aggarly.vision.vector.records;

public record QdrantCondition(
        String key,
        Object value,
        String operator // "EQUALS", "IN", "RANGE_GTE", "RANGE_LTE"
) {
    public static QdrantCondition equals(String key, Object value) {
        return new QdrantCondition(key, value, "EQUALS");
    }
}
