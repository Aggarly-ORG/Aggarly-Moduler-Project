package com.luna.aggarly.aiagent.controller;

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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
