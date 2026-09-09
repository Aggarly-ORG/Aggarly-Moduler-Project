package com.luna.aggarly.watchdog.controller;

import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.user.security.UserPrincipal;
import com.luna.aggarly.watchdog.dto.CreateWatchdogRequest;
import com.luna.aggarly.watchdog.dto.ToggleWatchdogRequest;
import com.luna.aggarly.watchdog.dto.WatchdogAlertDto;
import com.luna.aggarly.watchdog.service.WatchdogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/watchdogs")
@RequiredArgsConstructor
@Tag(name = "Watchdogs", description = "Autonomous Price Drop & Availability Radar APIs")
public class WatchdogController {

    private final WatchdogService watchdogService;

    @GetMapping
    @Operation(summary = "Fetch configured price & availability watchdogs", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<WatchdogAlertDto>>> getWatchdogs(@AuthenticationPrincipal UserPrincipal principal) {
        List<WatchdogAlertDto> list = watchdogService.getWatchdogs(principal.getUserId());
        return ApiResponse.ok(list, "Watchdogs retrieved successfully").toResponseEntity();
    }

    @PostMapping
    @Operation(summary = "Register new autonomous price drop / availability monitor", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<WatchdogAlertDto>> createWatchdog(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody CreateWatchdogRequest request) {
        WatchdogAlertDto created = watchdogService.createWatchdog(request, principal.getUserId());
        return ApiResponse.created(created, "Watchdog monitor registered").toResponseEntity();
    }

    @PutMapping("/{id}/toggle")
    @Operation(summary = "Pause or resume watchdog monitor scanning", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> toggleWatchdog(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody ToggleWatchdogRequest request) {
        boolean success = watchdogService.toggleWatchdog(id, Boolean.TRUE.equals(request.active()), principal.getUserId());
        return success ? ApiResponse.<Void>empty("Watchdog state toggled").toResponseEntity()
                       : ApiResponse.<Void>badRequest("Watchdog not found", "WATCHDOG_NOT_FOUND").toResponseEntity();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Decommission watchdog sensor alert", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> deleteWatchdog(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        boolean success = watchdogService.deleteWatchdog(id, principal.getUserId());
        return success ? ApiResponse.<Void>empty("Watchdog deleted").toResponseEntity()
                       : ApiResponse.<Void>badRequest("Watchdog not found", "WATCHDOG_NOT_FOUND").toResponseEntity();
    }
}