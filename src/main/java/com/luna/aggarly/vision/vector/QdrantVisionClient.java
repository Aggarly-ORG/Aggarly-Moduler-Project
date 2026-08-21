package com.luna.aggarly.vision.vector;

import com.luna.aggarly.vision.vector.records.QdrantCondition;
import com.luna.aggarly.vision.vector.records.QdrantFilter;
import com.luna.aggarly.vision.vector.records.QdrantMultiVectorPoint;
import com.luna.aggarly.vision.vector.records.QdrantSearchResult;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.PointIdFactory;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Collections.VectorParamsMap;
import io.qdrant.client.grpc.Common.Condition;
import io.qdrant.client.grpc.Common.FieldCondition;
import io.qdrant.client.grpc.Common.Filter;
import io.qdrant.client.grpc.Common.Match;
import io.qdrant.client.grpc.Common.PointId;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points.NamedVectors;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;
import io.qdrant.client.grpc.Points.Vector;
import io.qdrant.client.grpc.Points.Vectors;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class QdrantVisionClient {

    @org.springframework.beans.factory.annotation.Value("${aggarly.qdrant.host:localhost}")
    private String host;

    @org.springframework.beans.factory.annotation.Value("${aggarly.qdrant.port:6334}")
    private int port;

    @org.springframework.beans.factory.annotation.Value("${aggarly.qdrant.api-key:}")
    private String apiKey;

    @org.springframework.beans.factory.annotation.Value("${aggarly.qdrant.use-tls:false}")
    private boolean useTls;

    private QdrantClient qdrantClient;
    private boolean isAvailable = false;

    /**
     * In-memory fallback for local development/testing.
     *
     * collectionName -> pointId -> point
     */
    private final Map<String, Map<UUID, QdrantMultiVectorPoint>> inMemoryStore =
            new ConcurrentHashMap<>();


    // ============================================================
    // Lifecycle
    // ============================================================

    @PostConstruct
    public void init() {
        try {
            log.info(
                    "Initializing Qdrant client connecting to {}:{} (tls={})",
                    host,
                    port,
                    useTls
            );

            QdrantGrpcClient.Builder grpcBuilder =
                    QdrantGrpcClient.newBuilder(host, port, useTls);

            if (apiKey != null && !apiKey.isBlank()) {
                grpcBuilder.withApiKey(apiKey);
            }

            this.qdrantClient = new QdrantClient(grpcBuilder.build());
            this.isAvailable = true;

            log.info("Qdrant client initialized successfully");

            // Ensure collections exist with correct 768d named vector schemas
            ensureMultiVectorCollectionExists("property_images_v1", Map.of(
                    "image_vector", 768,
                    "caption_vector", 768
            ));
            ensureMultiVectorCollectionExists("property_visual_profiles_v1", Map.of(
                    "centroid_image_vector", 768,
                    "lifestyle_vector", 768
            ));
            ensureMultiVectorCollectionExists("property_descriptions_v1", Map.of(
                    "description_vector", 768
            ));

        } catch (Exception e) {
            log.warn(
                    "Could not initialize Qdrant at {}:{}."
                            + " Falling back to in-memory vector store: {}",
                    host,
                    port,
                    e.getMessage()
            );

            this.isAvailable = false;
        }
    }

    @PreDestroy
    public void close() {
        if (qdrantClient == null) {
            return;
        }

        try {
            qdrantClient.close();
        } catch (Exception e) {
            log.debug(
                    "Error closing Qdrant client: {}",
                    e.getMessage()
            );
        }
    }


    // ============================================================
    // Health
    // ============================================================

    public boolean isHealthy() {
        return isAvailable;
    }


    // ============================================================
    // Collections
    // ============================================================

    public void ensureMultiVectorCollectionExists(
            String collectionName,
            Map<String, Integer> namedVectorDims
    ) {

        // Always initialize fallback store.
        inMemoryStore.computeIfAbsent(
                collectionName,
                key -> new ConcurrentHashMap<>()
        );

        if (!isAvailable) {
            return;
        }

        try {
            boolean exists = qdrantClient
                    .collectionExistsAsync(collectionName)
                    .get(5, TimeUnit.SECONDS);

            if (exists) {
                return;
            }

            log.info(
                    "Creating Qdrant multi-vector collection: {} with schema: {}",
                    collectionName,
                    namedVectorDims
            );

            Map<String, VectorParams> vectorParamsMap =
                    new HashMap<>();

            for (Map.Entry<String, Integer> entry :
                    namedVectorDims.entrySet()) {

                vectorParamsMap.put(
                        entry.getKey(),
                        VectorParams.newBuilder()
                                .setSize(entry.getValue())
                                .setDistance(Distance.Cosine)
                                .build()
                );
            }

            qdrantClient.createCollectionAsync(
                    collectionName,
                    vectorParamsMap
            ).get(5, TimeUnit.SECONDS);

            log.info(
                    "Qdrant multi-vector collection {} created successfully",
                    collectionName
            );

        } catch (Exception e) {

            log.warn(
                    "Error creating Qdrant collection {}: {}."
                            + " Falling back to in-memory store.",
                    collectionName,
                    e.getMessage()
            );

            this.isAvailable = false;
        }
    }

    public void ensureCollectionExists(
            String collectionName,
            int vectorDim
    ) {
        ensureMultiVectorCollectionExists(
                collectionName,
                Map.of("default", vectorDim)
        );
    }


    // ============================================================
    // Upsert
    // ============================================================

    public void upsertMultiVectorPoint(
            String collection,
            UUID pointId,
            Map<String, float[]> namedVectors,
            Map<String, Object> payload
    ) {

        // Always update fallback store.
        Map<UUID, QdrantMultiVectorPoint> colStore =
                inMemoryStore.computeIfAbsent(
                        collection,
                        key -> new ConcurrentHashMap<>()
                );

        colStore.put(
                pointId,
                new QdrantMultiVectorPoint(
                        pointId,
                        namedVectors,
                        payload
                )
        );

        if (!isAvailable) {
            log.debug(
                    "Upserted point {} to in-memory store for collection {}",
                    pointId,
                    collection
            );

            return;
        }

        try {

            NamedVectors.Builder namedVectorsBuilder =
                    NamedVectors.newBuilder();

            for (Map.Entry<String, float[]> entry :
                    namedVectors.entrySet()) {

                Vector.Builder vectorBuilder =
                        Vector.newBuilder();

                for (float value : entry.getValue()) {
                    vectorBuilder.addData(value);
                }

                namedVectorsBuilder.putVectors(
                        entry.getKey(),
                        vectorBuilder.build()
                );
            }

            /*
             * Qdrant 1.18.x:
             *
             * PointId belongs to Common.
             *
             * PointIdFactory provides a convenient way to construct
             * UUID-based point IDs.
             */
            PointId qdrantPointId =
                    PointIdFactory.id(pointId);

            PointStruct.Builder pointBuilder =
                    PointStruct.newBuilder()
                            .setId(qdrantPointId)
                            .setVectors(
                                    Vectors.newBuilder()
                                            .setVectors(
                                                    namedVectorsBuilder.build()
                                            )
                                            .build()
                            );

            if (payload != null) {

                for (Map.Entry<String, Object> entry :
                        payload.entrySet()) {

                    if (entry.getValue() != null) {

                        pointBuilder.putPayload(
                                entry.getKey(),
                                convertToQdrantValue(entry.getValue())
                        );
                    }
                }
            }

            qdrantClient
                    .upsertAsync(
                            collection,
                            List.of(pointBuilder.build())
                    )
                    .get(5, TimeUnit.SECONDS);

            log.info(
                    "Successfully upserted point {} to Qdrant collection {}",
                    pointId,
                    collection
            );

        } catch (Exception e) {

            log.error(
                    "Failed to upsert point {} to Qdrant collection {}: {}",
                    pointId,
                    collection,
                    e.getMessage(),
                    e
            );
        }
    }


    // ============================================================
    // Search
    // ============================================================

    public List<QdrantSearchResult> searchByNamedVector(
            String collection,
            String vectorName,
            float[] queryVector,
            int limit,
            QdrantFilter filter,
            float minScore
    ) {

        if (!isAvailable) {
            return searchInMemory(
                    collection,
                    vectorName,
                    queryVector,
                    limit,
                    filter,
                    minScore
            );
        }

        try {

            SearchPoints.Builder searchBuilder =
                    SearchPoints.newBuilder()
                            .setCollectionName(collection)
                            .setVectorName(vectorName)
                            .setLimit(limit)
                            .setScoreThreshold(minScore)
                            .setWithPayload(
                                    io.qdrant.client.grpc.Points
                                            .WithPayloadSelector
                                            .newBuilder()
                                            .setEnable(true)
                                            .build()
                            );

            for (float value : queryVector) {
                searchBuilder.addVector(value);
            }

            Filter qdrantFilter =
                    buildFilter(filter);

            if (qdrantFilter != null) {
                searchBuilder.setFilter(qdrantFilter);
            }

            List<ScoredPoint> scoredPoints =
                    qdrantClient
                            .searchAsync(searchBuilder.build())
                            .get(5, TimeUnit.SECONDS);

            List<QdrantSearchResult> results =
                    new ArrayList<>();

            for (ScoredPoint scoredPoint : scoredPoints) {

                UUID id =
                        UUID.fromString(
                                scoredPoint
                                        .getId()
                                        .getUuid()
                        );

                Map<String, Object> payloadMap =
                        new HashMap<>();

                for (
                        Map.Entry<String, Value> entry :
                        scoredPoint.getPayloadMap().entrySet()
                ) {

                    payloadMap.put(
                            entry.getKey(),
                            convertFromQdrantValue(entry.getValue())
                    );
                }

                results.add(
                        new QdrantSearchResult(
                                id,
                                scoredPoint.getScore(),
                                payloadMap
                        )
                );
            }

            return results;

        } catch (Exception e) {

            log.warn(
                    "Qdrant search error in collection {}."
                            + " Falling back to in-memory search: {}",
                    collection,
                    e.getMessage()
            );

            return searchInMemory(
                    collection,
                    vectorName,
                    queryVector,
                    limit,
                    filter,
                    minScore
            );
        }
    }


    // ============================================================
    // Filters
    // ============================================================

    private Filter buildFilter(QdrantFilter filter) {

        if (filter == null
                || filter.must() == null
                || filter.must().isEmpty()) {

            return null;
        }

        Filter.Builder filterBuilder =
                Filter.newBuilder();

        for (QdrantCondition condition :
                filter.must()) {

            if (condition.value() instanceof String stringValue) {

                filterBuilder.addMust(
                        Condition.newBuilder()
                                .setField(
                                        FieldCondition.newBuilder()
                                                .setKey(condition.key())
                                                .setMatch(
                                                        Match.newBuilder()
                                                                .setKeyword(stringValue)
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                );

            } else if (condition.value() instanceof Boolean booleanValue) {

                filterBuilder.addMust(
                        Condition.newBuilder()
                                .setField(
                                        FieldCondition.newBuilder()
                                                .setKey(condition.key())
                                                .setMatch(
                                                        Match.newBuilder()
                                                                .setBoolean(booleanValue)
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                );
            }
        }

        return filterBuilder.build();
    }


    // ============================================================
    // Delete
    // ============================================================

    public void deletePoint(
            String collection,
            UUID pointId
    ) {

        Map<UUID, QdrantMultiVectorPoint> colStore =
                inMemoryStore.get(collection);

        if (colStore != null) {
            colStore.remove(pointId);
        }

        if (!isAvailable || qdrantClient == null) {
            return;
        }

        try {

            PointId qdrantPointId =
                    PointIdFactory.id(pointId);

            qdrantClient
                    .deleteAsync(
                            collection,
                            List.of(qdrantPointId)
                    )
                    .get(5, TimeUnit.SECONDS);

            log.debug(
                    "Deleted point {} from Qdrant collection {}",
                    pointId,
                    collection
            );

        } catch (Exception e) {

            log.warn(
                    "Error deleting point {} from Qdrant: {}",
                    pointId,
                    e.getMessage()
            );
        }
    }


    // ============================================================
    // In-memory fallback search
    // ============================================================

    private List<QdrantSearchResult> searchInMemory(
            String collection,
            String vectorName,
            float[] queryVector,
            int limit,
            QdrantFilter filter,
            float minScore
    ) {

        Map<UUID, QdrantMultiVectorPoint> colStore =
                inMemoryStore.getOrDefault(
                        collection,
                        Collections.emptyMap()
                );

        List<QdrantSearchResult> matches =
                new ArrayList<>();

        for (QdrantMultiVectorPoint point :
                colStore.values()) {

            if (!matchesFilter(point.payload(), filter)) {
                continue;
            }

            float[] vector =
                    point.vectors().get(vectorName);

            /*
             * Backward-compatible fallback:
             * if only one vector exists, use it.
             */
            if (vector == null
                    && point.vectors().size() == 1) {

                vector =
                        point.vectors()
                                .values()
                                .iterator()
                                .next();
            }

            if (vector == null) {
                continue;
            }

            float similarity =
                    computeCosineSimilarity(
                            queryVector,
                            vector
                    );

            if (similarity >= minScore) {

                matches.add(
                        new QdrantSearchResult(
                                point.id(),
                                similarity,
                                point.payload()
                        )
                );
            }
        }

        matches.sort(
                (a, b) ->
                        Float.compare(
                                b.score(),
                                a.score()
                        )
        );

        if (matches.size() > limit) {
            return matches.subList(0, limit);
        }

        return matches;
    }


    private boolean matchesFilter(
            Map<String, Object> payload,
            QdrantFilter filter
    ) {

        if (filter == null
                || filter.must() == null
                || filter.must().isEmpty()) {

            return true;
        }

        if (payload == null) {
            return false;
        }

        for (QdrantCondition condition :
                filter.must()) {

            Object actual =
                    payload.get(condition.key());

            Object expected =
                    condition.value();

            if (actual == null
                    || !actual.equals(expected)) {

                return false;
            }
        }

        return true;
    }


    // ============================================================
    // Cosine similarity
    // ============================================================

    private float computeCosineSimilarity(
            float[] a,
            float[] b
    ) {

        if (a == null
                || b == null
                || a.length != b.length
                || a.length == 0) {

            return 0.0f;
        }

        float dot = 0.0f;
        float normA = 0.0f;
        float normB = 0.0f;

        for (int i = 0; i < a.length; i++) {

            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA <= 0.0f
                || normB <= 0.0f) {

            return 0.0f;
        }

        return (float) (
                dot /
                        (Math.sqrt(normA) * Math.sqrt(normB))
        );
    }


    // ============================================================
    // Qdrant payload conversion
    // ============================================================

    private Value convertToQdrantValue(Object value) {

        if (value instanceof String stringValue) {

            return Value.newBuilder()
                    .setStringValue(stringValue)
                    .build();

        } else if (value instanceof Boolean booleanValue) {

            return Value.newBuilder()
                    .setBoolValue(booleanValue)
                    .build();

        } else if (value instanceof Integer integerValue) {

            return Value.newBuilder()
                    .setIntegerValue(integerValue)
                    .build();

        } else if (value instanceof Long longValue) {

            return Value.newBuilder()
                    .setIntegerValue(longValue)
                    .build();

        } else if (value instanceof Double doubleValue) {

            return Value.newBuilder()
                    .setDoubleValue(doubleValue)
                    .build();

        } else if (value instanceof Float floatValue) {

            return Value.newBuilder()
                    .setDoubleValue(floatValue)
                    .build();
        }

        return Value.newBuilder()
                .setStringValue(value.toString())
                .build();
    }


    private Object convertFromQdrantValue(Value value) {

        if (value.hasStringValue()) {
            return value.getStringValue();
        }

        if (value.hasBoolValue()) {
            return value.getBoolValue();
        }

        if (value.hasIntegerValue()) {
            return value.getIntegerValue();
        }

        if (value.hasDoubleValue()) {
            return value.getDoubleValue();
        }

        return value.toString();
    }
}