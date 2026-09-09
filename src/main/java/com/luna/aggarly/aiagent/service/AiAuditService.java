package com.luna.aggarly.aiagent.service;

import com.luna.aggarly.aiagent.dto.AgentPerformanceMetricsResponse;
import com.luna.aggarly.aiagent.dto.AiUsageStatsResponse;
import com.luna.aggarly.aiagent.dto.ToolCallLogResponse;
import com.luna.aggarly.aiagent.entity.AiToolInvocation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

public interface AiAuditService {
    Page<ToolCallLogResponse> getToolInvocations(UUID userId, String toolName, Boolean success, Pageable pageable);
    AiUsageStatsResponse getUsageStats(UUID userId);
    SseEmitter subscribeToStream();
    List<AgentPerformanceMetricsResponse> getAgentPerformanceBreakdown();
    ToolCallLogResponse replayInvocation(UUID id);
    void flushCache();
    void flushWeights();
    void logInvocation(AiToolInvocation invocation);
}
