package com.luna.aggarly.vision.vector.records;

public record QdrantCondition(
        String key,
        Object value,
        String operator // "EQUALS", "IN", "RANGE_GTE", "RANGE_LTE"
) {
    public static QdrantCondition equals(String key, Object value) {
        return new QdrantCondition(key, value, "EQUALS");
    }

    public static QdrantCondition in(String key, Object value) {
        return new QdrantCondition(key, value, "IN");
    }

    public static QdrantCondition rangeGte(String key, Number value) {
        return new QdrantCondition(key, value, "RANGE_GTE");
    }

    public static QdrantCondition rangeLte(String key, Number value) {
        return new QdrantCondition(key, value, "RANGE_LTE");
    }
}
