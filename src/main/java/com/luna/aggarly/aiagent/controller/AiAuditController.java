package com.luna.aggarly.aiagent.controller;

import com.luna.aggarly.aiagent.dto.AgentPerformanceMetricsResponse;
import com.luna.aggarly.aiagent.dto.AiUsageStatsResponse;
import com.luna.aggarly.aiagent.dto.ToolCallLogResponse;
import com.luna.aggarly.aiagent.service.AiAuditService;
import com.luna.aggarly.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

/**
 * Controller for administrative audit logs, agent tool execution records, and model token usage statistics.
 */
@RestController
@RequestMapping("/api/v1/ai/audit")
@RequiredArgsConstructor
@Tag(name = "AI Audit", description = "AI Agent Tool Invocation & Usage Analytics APIs")
public class AiAuditController {

    private final AiAuditService auditService;

    @GetMapping("/invocations")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Query agent tool call execution audit logs (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<ToolCallLogResponse>>> getToolInvocations(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String toolName,
            @RequestParam(required = false) Boolean success,
            Pageable pageable) {
        Page<ToolCallLogResponse> page = auditService.getToolInvocations(userId, toolName, success, pageable);
        return ApiResponse.paged(page, "Tool invocations retrieved successfully").toResponseEntity();
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get aggregated LLM usage and token statistics (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<AiUsageStatsResponse>> getUsageStats(
            @RequestParam(required = false) UUID userId) {
        AiUsageStatsResponse stats = auditService.getUsageStats(userId);
        return ApiResponse.ok(stats, "AI usage stats retrieved successfully").toResponseEntity();
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Subscribe to live real-time agent invocation telemetry stream via SSE (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public SseEmitter streamInvocations() {
        return auditService.subscribeToStream();
    }

    @GetMapping("/agents/breakdown")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get per-agent workload distribution and performance metrics (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<AgentPerformanceMetricsResponse>>> getAgentPerformanceBreakdown() {
        List<AgentPerformanceMetricsResponse> breakdown = auditService.getAgentPerformanceBreakdown();
        return ApiResponse.ok(breakdown, "Agent performance breakdown retrieved successfully").toResponseEntity();
    }

    @PostMapping("/invocations/{id}/replay")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Replay a tool invocation in an isolated sandbox environment (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<ToolCallLogResponse>> replayInvocation(@PathVariable UUID id) {
        ToolCallLogResponse replay = auditService.replayInvocation(id);
        return ApiResponse.ok(replay, "Tool invocation replayed in sandbox successfully").toResponseEntity();
    }

    @PostMapping("/cache/flush")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Flush in-memory inference and response caches (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<String>> flushCache() {
        auditService.flushCache();
        return ApiResponse.ok("AI in-memory cache flushed successfully", "In-memory cache flushed").toResponseEntity();
    }

    @PostMapping("/flush-weights")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Flush GPU KV-cache weights for idle models (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<String>> flushWeights() {
        auditService.flushWeights();
        return ApiResponse.ok("GPU model KV-cache and weights flushed successfully", "Weights flushed").toResponseEntity();
    }
}
