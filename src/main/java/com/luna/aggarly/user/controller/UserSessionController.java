package com.luna.aggarly.user.controller;

import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.user.dto.response.UserSessionResponse;
import com.luna.aggarly.user.security.UserPrincipal;
import com.luna.aggarly.user.service.UserSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/sessions")
@RequiredArgsConstructor
@Tag(name = "Active Sessions", description = "Device & Active Session Management and Revocation APIs")
public class UserSessionController {

    private final UserSessionService userSessionService;

    @GetMapping
    @Operation(summary = "Get all active sessions for current user", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<UserSessionResponse>>> getActiveSessions(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<UserSessionResponse> sessions = userSessionService.getActiveSessions(
                principal.getUserId(),
                principal.getSessionId()
        );
        return ApiResponse.ok(sessions, "Active sessions retrieved successfully").toResponseEntity();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Revoke a specific active session", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> revokeSession(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") UUID id) {
        userSessionService.revokeSession(principal.getUserId(), id);
        return ApiResponse.<Void>empty("Session revoked successfully").toResponseEntity();
    }

    @DeleteMapping
    @Operation(summary = "Revoke all other active sessions (Sign Out All Other Devices)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> revokeAllOtherSessions(
            @AuthenticationPrincipal UserPrincipal principal) {
        userSessionService.revokeAllOtherSessions(principal.getUserId(), principal.getSessionId());
        return ApiResponse.<Void>empty("All other active sessions revoked successfully").toResponseEntity();
    }
}
