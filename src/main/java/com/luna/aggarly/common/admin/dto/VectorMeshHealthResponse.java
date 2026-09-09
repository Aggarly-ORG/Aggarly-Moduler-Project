package com.luna.aggarly.common.admin.dto;

public record VectorMeshHealthResponse(
    String qdrantStatus,
    long totalVectors,
    double hnswIndexHealthPercent,
    double throughputRps,
    double memoryAllocatedMb,
    String pgvectorSyncStatus,
    double replicationLagMs
) {}
