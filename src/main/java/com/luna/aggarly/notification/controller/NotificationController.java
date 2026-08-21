package com.luna.aggarly.notification.controller;

import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.notification.dto.request.TestNotificationRequest;
import com.luna.aggarly.notification.dto.response.NotificationResponse;
import com.luna.aggarly.notification.dto.response.NotificationSummaryResponse;
import com.luna.aggarly.notification.entity.enums.NotificationCategory;
import com.luna.aggarly.notification.service.NotificationDispatcher;
import com.luna.aggarly.notification.service.NotificationService;
import com.luna.aggarly.user.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller managing notification inbox, read receipts, and system dispatch triggers.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "User Notification Inbox & Read APIs")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationDispatcher notificationDispatcher;

    @GetMapping
    @Operation(summary = "Get paginated list of user notifications", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getNotifications(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            Pageable pageable) {
        Page<NotificationResponse> response = unreadOnly
                ? notificationService.getUnreadNotifications(principal.getUserId(), pageable)
                : notificationService.getUserNotifications(principal.getUserId(), pageable);
        return ApiResponse.paged(response, "Notifications retrieved successfully").toResponseEntity();
    }

    @GetMapping("/unread-summary")
    @Operation(summary = "Get unread count and recent notifications badge", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<NotificationSummaryResponse>> getUnreadSummary(
            @AuthenticationPrincipal UserPrincipal principal) {
        NotificationSummaryResponse response = notificationService.getUnreadSummary(principal.getUserId());
        return ApiResponse.ok(response, "Unread summary retrieved successfully").toResponseEntity();
    }

    @PostMapping("/test-send")
    @Operation(summary = "Send a test notification to the authenticated user", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Map<String, Object>>> testSendNotification(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) TestNotificationRequest req) {
        UUID userId = principal.getUserId();
        String title = (req != null && req.title() != null && !req.title().isBlank()) ? req.title() : "Test Notification Alert";
        String message = (req != null && req.message() != null && !req.message().isBlank()) ? req.message() : "This is a real-time test notification sent at " + Instant.now();
        NotificationCategory cat = (req != null && req.category() != null) ? req.category() : NotificationCategory.ALERTS;
        String type = (req != null && req.type() != null && !req.type().isBlank()) ? req.type() : "SYSTEM_TEST";
        String dataJson = (req != null && req.dataJson() != null) ? req.dataJson() : "{}";

        notificationDispatcher.dispatch(userId, cat, type, title, message, dataJson, null, null);
        Map<String, Object> body = Map.of("success", (Object) true, "message", (Object) ("Test notification dispatched to user " + userId));
        return ApiResponse.ok(body, "Notification sent").toResponseEntity();
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark a notification as read", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        notificationService.markAsRead(id, principal.getUserId());
        return ApiResponse.<Void>empty("Notification marked as read").toResponseEntity();
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark all notifications as read", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Integer>> markAllAsRead(
            @AuthenticationPrincipal UserPrincipal principal) {
        int count = notificationService.markAllAsRead(principal.getUserId());
        return ApiResponse.ok(count, "All notifications marked as read").toResponseEntity();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete (soft-delete) a notification", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> deleteNotification(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        notificationService.deleteNotification(id, principal.getUserId());
        return ApiResponse.<Void>empty("Notification deleted successfully").toResponseEntity();
    }
}
