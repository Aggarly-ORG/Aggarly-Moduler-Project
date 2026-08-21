package com.luna.aggarly.cleaning.controller;

import com.luna.aggarly.cleaning.dto.request.AssignCleanerRequest;
import com.luna.aggarly.cleaning.dto.request.CompleteInspectionRequest;
import com.luna.aggarly.cleaning.dto.request.CreateCleaningTaskRequest;
import com.luna.aggarly.cleaning.dto.response.CleaningTaskResponse;
import com.luna.aggarly.cleaning.dto.response.CleaningTaskSummaryResponse;
import com.luna.aggarly.cleaning.entity.enums.CleaningStatus;
import com.luna.aggarly.cleaning.service.CleaningTaskService;
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
 * Controller managing turnover cleaning schedules, task assignments, inspection ratings, and status transitions.
 */
@RestController
@RequestMapping("/api/v1/cleaning/tasks")
@RequiredArgsConstructor
@Tag(name = "Cleaning Tasks", description = "Turnover & Cleaning Management APIs")
public class CleaningTaskController {

    private final CleaningTaskService cleaningTaskService;

    @PreAuthorize("hasAnyRole('HOST','ADMIN')")
    @PostMapping
    @Operation(summary = "Schedule a cleaning task manually (HOST only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<CleaningTaskResponse>> createTask(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateCleaningTaskRequest request) {
        CleaningTaskResponse response = cleaningTaskService.createCleaningTask(request, principal.getUserId());
        return ApiResponse.created(response, "Cleaning task created successfully").toResponseEntity();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get cleaning task details by ID", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<CleaningTaskResponse>> getTaskById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        CleaningTaskResponse response = cleaningTaskService.getCleaningTaskById(id, principal.getUserId());
        return ApiResponse.ok(response, "Cleaning task details retrieved successfully").toResponseEntity();
    }

    @PreAuthorize("hasAnyRole('HOST','ADMIN')")
    @GetMapping("/host")
    @Operation(summary = "List cleaning tasks for the current host", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<CleaningTaskSummaryResponse>>> getHostTasks(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) CleaningStatus status,
            @RequestParam(required = false) UUID propertyId,
            Pageable pageable) {
        Page<CleaningTaskSummaryResponse> response = cleaningTaskService.getHostTasks(principal.getUserId(), status, propertyId, pageable);
        return ApiResponse.paged(response, "Host cleaning tasks retrieved successfully").toResponseEntity();
    }

    @GetMapping("/cleaner")
    @Operation(summary = "List cleaning tasks assigned to the cleaner", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<CleaningTaskSummaryResponse>>> getCleanerTasks(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) CleaningStatus status,
            Pageable pageable) {
        Page<CleaningTaskSummaryResponse> response = cleaningTaskService.getCleanerTasks(principal.getUserId(), status, pageable);
        return ApiResponse.paged(response, "Cleaner tasks retrieved successfully").toResponseEntity();
    }

    @PreAuthorize("hasAnyRole('HOST','ADMIN')")
    @GetMapping("/property/{propertyId}")
    @Operation(summary = "Get cleaning history for a property", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<CleaningTaskSummaryResponse>>> getPropertyCleaningHistory(
            @PathVariable UUID propertyId,
            Pageable pageable) {
        Page<CleaningTaskSummaryResponse> response = cleaningTaskService.getPropertyCleaningHistory(propertyId, pageable);
        return ApiResponse.paged(response, "Property cleaning history retrieved successfully").toResponseEntity();
    }

    @PreAuthorize("hasAnyRole('HOST','ADMIN')")
    @PatchMapping("/{id}/assign")
    @Operation(summary = "Assign a cleaner to a cleaning task", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<CleaningTaskResponse>> assignCleaner(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody AssignCleanerRequest request) {
        CleaningTaskResponse response = cleaningTaskService.assignCleaner(id, request, principal.getUserId());
        return ApiResponse.ok(response, "Cleaner assigned successfully").toResponseEntity();
    }

    @PatchMapping("/{id}/start")
    @Operation(summary = "Mark a cleaning task IN_PROGRESS (Cleaner/Host)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<CleaningTaskResponse>> startCleaning(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        CleaningTaskResponse response = cleaningTaskService.startCleaning(id, principal.getUserId());
        return ApiResponse.ok(response, "Cleaning started").toResponseEntity();
    }

    @PatchMapping("/{id}/complete")
    @Operation(summary = "Mark a cleaning task COMPLETED (Cleaner/Host)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<CleaningTaskResponse>> completeCleaning(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam(required = false) String cleanerNotes) {
        CleaningTaskResponse response = cleaningTaskService.completeCleaning(id, principal.getUserId(), cleanerNotes);
        return ApiResponse.ok(response, "Cleaning completed").toResponseEntity();
    }

    @PreAuthorize("hasAnyRole('HOST','ADMIN')")
    @PatchMapping("/{id}/skip")
    @Operation(summary = "Skip a scheduled cleaning task (Host only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<CleaningTaskResponse>> skipCleaning(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "Skipped by host") String reason) {
        CleaningTaskResponse response = cleaningTaskService.skipCleaning(id, principal.getUserId(), reason);
        return ApiResponse.ok(response, "Cleaning task skipped").toResponseEntity();
    }

    @PreAuthorize("hasAnyRole('HOST','ADMIN')")
    @PatchMapping("/{id}/inspect")
    @Operation(summary = "Inspect and rate a completed cleaning task (Host only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<CleaningTaskResponse>> inspectCleaning(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody CompleteInspectionRequest request) {
        CleaningTaskResponse response = cleaningTaskService.inspectCleaning(id, request, principal.getUserId());
        return ApiResponse.ok(response, "Cleaning task inspected and approved").toResponseEntity();
    }
}
