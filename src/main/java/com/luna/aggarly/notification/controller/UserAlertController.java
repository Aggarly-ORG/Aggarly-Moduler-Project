package com.luna.aggarly.notification.controller;

import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.notification.dto.request.CreateUserAlertRequest;
import com.luna.aggarly.notification.dto.response.UserAlertResponse;
import com.luna.aggarly.notification.service.UserAlertService;
import com.luna.aggarly.user.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controller managing price drop and availability watchdog alerts.
 */
@RestController
@RequestMapping("/api/v1/notifications/alerts")
@RequiredArgsConstructor
@Tag(name = "User Alerts", description = "Price Drop & Availability Watchdog Alerts APIs")
public class UserAlertController {

    private final UserAlertService userAlertService;

    @PostMapping
    @Operation(summary = "Create a price or availability watchdog alert", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<UserAlertResponse>> createAlert(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateUserAlertRequest request) {
        UserAlertResponse response = userAlertService.createAlert(principal.getUserId(), request);
        return ApiResponse.created(response, "Watchdog alert created successfully").toResponseEntity();
    }

    @GetMapping
    @Operation(summary = "List user active watchdog alerts", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<UserAlertResponse>>> getAlerts(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<UserAlertResponse> response = userAlertService.getUserAlerts(principal.getUserId());
        return ApiResponse.ok(response, "User alerts retrieved successfully").toResponseEntity();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate a watchdog alert", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> deleteAlert(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        userAlertService.deleteAlert(id, principal.getUserId());
        return ApiResponse.<Void>empty("Alert deactivated successfully").toResponseEntity();
    }
}
