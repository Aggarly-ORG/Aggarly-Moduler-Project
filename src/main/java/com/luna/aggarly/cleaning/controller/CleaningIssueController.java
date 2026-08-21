package com.luna.aggarly.cleaning.controller;

import com.luna.aggarly.cleaning.dto.request.ReportCleaningIssueRequest;
import com.luna.aggarly.cleaning.dto.response.CleaningIssueResponse;
import com.luna.aggarly.cleaning.service.CleaningIssueService;
import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.user.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controller managing damage incidents and maintenance issues discovered during cleaning turnover.
 */
@RestController
@RequestMapping("/api/v1/cleaning/issues")
@RequiredArgsConstructor
@Tag(name = "Cleaning Issues", description = "Damage & Maintenance Incident APIs")
public class CleaningIssueController {

    private final CleaningIssueService issueService;

    @PostMapping("/tasks/{taskId}")
    @Operation(summary = "Report a damage or maintenance issue found during cleaning", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<CleaningIssueResponse>> reportIssue(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID taskId,
            @Valid @RequestBody ReportCleaningIssueRequest request) {
        CleaningIssueResponse response = issueService.reportIssue(taskId, request, principal.getUserId());
        return ApiResponse.created(response, "Cleaning issue reported successfully").toResponseEntity();
    }

    @PreAuthorize("hasAnyRole('HOST','ADMIN')")
    @PatchMapping("/{id}/resolve")
    @Operation(summary = "Mark a reported issue resolved (Host only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<CleaningIssueResponse>> resolveIssue(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam String resolutionNotes) {
        CleaningIssueResponse response = issueService.resolveIssue(id, resolutionNotes, principal.getUserId());
        return ApiResponse.ok(response, "Cleaning issue resolved successfully").toResponseEntity();
    }

    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "Get issues reported for a specific cleaning task", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<CleaningIssueResponse>>> getIssuesByTaskId(
            @PathVariable UUID taskId) {
        List<CleaningIssueResponse> response = issueService.getIssuesByTaskId(taskId);
        return ApiResponse.ok(response, "Issues for task retrieved").toResponseEntity();
    }

    @PreAuthorize("hasAnyRole('HOST','ADMIN')")
    @GetMapping("/property/{propertyId}")
    @Operation(summary = "Get issues reported for a property", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<CleaningIssueResponse>>> getIssuesByProperty(
            @PathVariable UUID propertyId,
            Pageable pageable) {
        Page<CleaningIssueResponse> response = issueService.getIssuesByProperty(propertyId, pageable);
        return ApiResponse.paged(response, "Property cleaning issues retrieved").toResponseEntity();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/unresolved")
    @Operation(summary = "Get all unresolved platform cleaning issues (Admin only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<CleaningIssueResponse>>> getUnresolvedIssues(
            Pageable pageable) {
        Page<CleaningIssueResponse> response = issueService.getUnresolvedIssues(pageable);
        return ApiResponse.paged(response, "Unresolved cleaning issues retrieved").toResponseEntity();
    }
}
