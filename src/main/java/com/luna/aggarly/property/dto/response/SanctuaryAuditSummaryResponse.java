package com.luna.aggarly.property.dto.response;

public record SanctuaryAuditSummaryResponse(
    long totalSanctuaries,
    long verifiedCount,
    long pendingAuditCount,
    double complianceScorePercent,
    long activeCatalogCount,
    long delistedCount
) {}
