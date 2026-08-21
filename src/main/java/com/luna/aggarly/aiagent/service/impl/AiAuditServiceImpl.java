package com.luna.aggarly.aiagent.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.aiagent.dto.AiUsageStatsResponse;
import com.luna.aggarly.aiagent.dto.ToolCallLogResponse;
import com.luna.aggarly.aiagent.entity.AiToolInvocation;
import com.luna.aggarly.aiagent.mapper.AiMessageMapper;
import com.luna.aggarly.aiagent.repository.AiToolInvocationRepository;
import com.luna.aggarly.aiagent.service.AiAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAuditServiceImpl implements AiAuditService {

    private final AiToolInvocationRepository invocationRepository;
    private final AiMessageMapper mapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional(readOnly = true)
    public Page<ToolCallLogResponse> getToolInvocations(UUID userId, String toolName, Boolean success, Pageable pageable) {
        log.info("Fetching AI tool invocation audit log for userId={}, toolName={}", userId, toolName);
        return invocationRepository.findAll(pageable).map(mapper::toLogResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AiUsageStatsResponse getUsageStats(UUID userId) {
        log.info("Computing AI usage and cost statistics for userId={}", userId);
        List<AiToolInvocation> invocations = invocationRepository.findAll();

        long total = invocations.size();
        long successCount = invocations.stream().filter(AiToolInvocation::isSuccess).count();
        long failedCount = total - successCount;

        long promptTokens = invocations.stream().mapToInt(i -> i.getPromptTokens() != null ? i.getPromptTokens() : 0).sum();
        long completionTokens = invocations.stream().mapToInt(i -> i.getCompletionTokens() != null ? i.getCompletionTokens() : 0).sum();
        double totalCost = invocations.stream().mapToDouble(i -> i.getEstimatedCostUsd() != null ? i.getEstimatedCostUsd() : 0.0).sum();
        double avgDuration = invocations.stream().mapToLong(AiToolInvocation::getDurationMs).average().orElse(0.0);

        return new AiUsageStatsResponse(
                total,
                successCount,
                failedCount,
                promptTokens,
                completionTokens,
                totalCost,
                avgDuration
        );
    }
}
