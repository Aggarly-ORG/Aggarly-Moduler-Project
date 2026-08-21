package com.luna.aggarly.user.controller;

import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.user.dto.response.UserProfileSummaryResponse;
import com.luna.aggarly.user.entity.User;
import com.luna.aggarly.user.mapper.UserMapper;
import com.luna.aggarly.user.repository.UserRepository;
import com.luna.aggarly.user.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users Directory", description = "Public User Profiles & Directory Search for Direct Messaging")
public class UserController {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @GetMapping("/{id}")
    @Operation(summary = "Get public user profile by ID", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<UserProfileSummaryResponse>> getUserById(@PathVariable UUID id) {
        return userRepository.findById(id)
                .map(userMapper::toSummaryResponse)
                .map(summary -> ApiResponse.ok(summary, "User profile retrieved successfully").toResponseEntity())
                .orElseGet(() -> ApiResponse.<UserProfileSummaryResponse>notFound("User not found with id: " + id).toResponseEntity());
    }

    @GetMapping("/search")
    @Operation(summary = "Search users for direct messaging by name, username, or email", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<UserProfileSummaryResponse>>> searchUsers(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false, defaultValue = "") String query,
            @RequestParam(required = false, defaultValue = "10") int limit) {
        UUID currentUserId = principal != null ? principal.getUserId() : null;
        int max = Math.min(Math.max(1, limit), 50);

        List<User> users;
        if (query == null || query.trim().isBlank()) {
            users = currentUserId != null
                    ? userRepository.findRecentUsers(currentUserId, PageRequest.of(0, max))
                    : userRepository.findAll(PageRequest.of(0, max)).getContent();
        } else {
            users = userRepository.searchUsers(query.trim(), PageRequest.of(0, max)).stream()
                    .filter(u -> currentUserId == null || !u.getId().equals(currentUserId))
                    .toList();
        }

        List<UserProfileSummaryResponse> response = users.stream()
                .map(userMapper::toSummaryResponse)
                .toList();

        return ApiResponse.ok(response, "Users matching query retrieved").toResponseEntity();
    }
}
