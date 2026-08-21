package com.luna.aggarly.notification.controller;

import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.notification.dto.request.UpdateNotificationPreferenceRequest;
import com.luna.aggarly.notification.dto.response.NotificationPreferenceResponse;
import com.luna.aggarly.notification.service.UserNotificationPreferenceService;
import com.luna.aggarly.user.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller managing notification delivery preferences across email, SMS, and in-app channels.
 */
@RestController
@RequestMapping("/api/v1/notifications/preferences")
@RequiredArgsConstructor
@Tag(name = "Notification Preferences", description = "User Notification Channel & Category Settings")
public class NotificationPreferenceController {

    private final UserNotificationPreferenceService preferenceService;

    @GetMapping
    @Operation(summary = "Get user notification preferences", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<NotificationPreferenceResponse>>> getPreferences(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<NotificationPreferenceResponse> response = preferenceService.getUserPreferences(principal.getUserId());
        return ApiResponse.ok(response, "Preferences retrieved successfully").toResponseEntity();
    }

    @PutMapping
    @Operation(summary = "Update notification preferences for a category", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> updatePreference(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateNotificationPreferenceRequest request) {
        NotificationPreferenceResponse response = preferenceService.updatePreference(principal.getUserId(), request);
        return ApiResponse.ok(response, "Preference updated successfully").toResponseEntity();
    }
}
