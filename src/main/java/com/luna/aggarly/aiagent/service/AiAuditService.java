package com.luna.aggarly.aiagent.service;

import com.luna.aggarly.aiagent.dto.AiUsageStatsResponse;
import com.luna.aggarly.aiagent.dto.ToolCallLogResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AiAuditService {
    Page<ToolCallLogResponse> getToolInvocations(UUID userId, String toolName, Boolean success, Pageable pageable);
    AiUsageStatsResponse getUsageStats(UUID userId);
}
