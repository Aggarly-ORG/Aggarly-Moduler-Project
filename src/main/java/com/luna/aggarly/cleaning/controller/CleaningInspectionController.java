package com.luna.aggarly.cleaning.controller;

import com.luna.aggarly.cleaning.dto.request.SubmitChecklistItemRequest;
import com.luna.aggarly.cleaning.dto.response.CleaningChecklistItemResponse;
import com.luna.aggarly.cleaning.dto.response.CleaningChecklistResponse;
import com.luna.aggarly.cleaning.dto.response.CleaningPhotoResponse;
import com.luna.aggarly.cleaning.entity.enums.CleaningPhotoType;
import com.luna.aggarly.cleaning.service.CleaningChecklistService;
import com.luna.aggarly.cleaning.service.CleaningPhotoService;
import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.user.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
 * Controller managing room checklists and photo proof uploads for cleaning tasks.
 */
@RestController
@RequestMapping("/api/v1/cleaning")
@RequiredArgsConstructor
@Tag(name = "Cleaning Inspection", description = "Checklists & Photo Proof Verification APIs")
public class CleaningInspectionController {

    private final CleaningChecklistService checklistService;
    private final CleaningPhotoService photoService;

    @GetMapping("/tasks/{taskId}/checklists")
    @Operation(summary = "Get room checklists for a cleaning task", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<CleaningChecklistResponse>>> getChecklists(
            @PathVariable UUID taskId) {
        List<CleaningChecklistResponse> response = checklistService.getChecklistsByTaskId(taskId);
        return ApiResponse.ok(response, "Checklists retrieved successfully").toResponseEntity();
    }

    @PatchMapping("/checklist-items/{itemId}")
    @Operation(summary = "Update a checklist item completion status", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<CleaningChecklistItemResponse>> updateChecklistItem(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID itemId,
            @Valid @RequestBody SubmitChecklistItemRequest request) {
        CleaningChecklistItemResponse response = checklistService.updateChecklistItem(itemId, request, principal.getUserId());
        return ApiResponse.ok(response, "Checklist item updated successfully").toResponseEntity();
    }

    @PostMapping("/tasks/{taskId}/photos")
    @Operation(summary = "Record uploaded photo proof for a cleaning task", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<CleaningPhotoResponse>> uploadPhoto(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID taskId,
            @RequestParam(required = false) String roomName,
            @RequestParam(defaultValue = "AFTER") CleaningPhotoType photoType,
            @RequestParam String objectKey) {
        CleaningPhotoResponse response = photoService.uploadCleaningPhoto(taskId, roomName, photoType, objectKey, principal.getUserId());
        return ApiResponse.created(response, "Cleaning photo proof recorded").toResponseEntity();
    }

    @GetMapping("/tasks/{taskId}/photos")
    @Operation(summary = "Get all verification photos for a cleaning task", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<CleaningPhotoResponse>>> getPhotos(
            @PathVariable UUID taskId) {
        List<CleaningPhotoResponse> response = photoService.getPhotosByTaskId(taskId);
        return ApiResponse.ok(response, "Cleaning photos retrieved successfully").toResponseEntity();
    }
}
