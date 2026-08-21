package com.luna.aggarly.vision.vector.records;

import java.util.ArrayList;
import java.util.List;

public record QdrantFilter(
        List<QdrantCondition> must,
        List<QdrantCondition> should,
        List<QdrantCondition> mustNot
) {
    public static QdrantFilter empty() {
        return new QdrantFilter(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    public static QdrantFilter must(List<QdrantCondition> conditions) {
        return new QdrantFilter(conditions != null ? conditions : new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }
}
