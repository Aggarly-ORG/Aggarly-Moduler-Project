package com.luna.aggarly.aiagent.controller;

import com.luna.aggarly.aiagent.service.AiAuditService;
import com.luna.aggarly.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/admin")
@RequiredArgsConstructor
@Tag(name = "AI Admin", description = "AI Cluster Administrative Operations")
public class AiAdminController {

    private final AiAuditService auditService;

    @PostMapping("/flush-weights")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Flush GPU KV-cache weights for idle models (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<String>> flushWeights() {
        auditService.flushWeights();
        return ApiResponse.ok("GPU model KV-cache and weights flushed successfully", "Weights flushed").toResponseEntity();
    }
}
