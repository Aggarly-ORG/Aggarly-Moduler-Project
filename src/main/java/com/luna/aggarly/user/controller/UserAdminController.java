package com.luna.aggarly.user.controller;

import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.user.dto.request.UserStatusUpdateDto;
import com.luna.aggarly.user.dto.response.UserAdminResponse;
import com.luna.aggarly.user.dto.response.UserSessionResponse;
import com.luna.aggarly.user.service.UserAdminService;
import com.luna.aggarly.user.service.UserSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "User Admin", description = "Administrative User Identity Directory & Session Governance APIs")
public class UserAdminController {

    private final UserAdminService userAdminService;
    private final UserSessionService userSessionService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Query user directory with role and status filters (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<UserAdminResponse>>> getAllUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        Page<UserAdminResponse> page = userAdminService.getAllUsersAdmin(role, status, search, pageable);
        return ApiResponse.paged(page, "Users directory retrieved successfully").toResponseEntity();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Suspend, verify, or reinstate user account (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<UserAdminResponse>> updateUserStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UserStatusUpdateDto request) {
        UserAdminResponse response = userAdminService.updateUserStatus(id, request.getStatus(), request.getReason());
        return ApiResponse.ok(response, "User status updated successfully").toResponseEntity();
    }

    @GetMapping("/{userId}/sessions")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all active device sessions for a user (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<UserSessionResponse>>> getUserSessionsAdmin(
            @PathVariable UUID userId) {
        List<UserSessionResponse> sessions = userSessionService.getActiveSessions(userId, null);
        return ApiResponse.ok(sessions, "User active sessions retrieved successfully").toResponseEntity();
    }

    @DeleteMapping("/{userId}/sessions/{sessionId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Remotely terminate a specific user session (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> revokeUserSessionAdmin(
            @PathVariable UUID userId,
            @PathVariable UUID sessionId) {
        userSessionService.revokeSession(userId, sessionId);
        return ApiResponse.<Void>empty("User session revoked successfully").toResponseEntity();
    }

    @DeleteMapping("/{userId}/sessions")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Remotely terminate all active sessions for a user (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> revokeAllUserSessionsAdmin(
            @PathVariable UUID userId) {
        userSessionService.revokeAllSessions(userId);
        return ApiResponse.<Void>empty("All user sessions revoked successfully").toResponseEntity();
    }

    @PostMapping("/{userId}/force-reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Force out-of-band cryptographic password reset challenge (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> forcePasswordReset(
            @PathVariable UUID userId) {
        userAdminService.forcePasswordReset(userId);
        return ApiResponse.<Void>empty("Password reset challenge dispatched and active sessions invalidated").toResponseEntity();
    }
}
