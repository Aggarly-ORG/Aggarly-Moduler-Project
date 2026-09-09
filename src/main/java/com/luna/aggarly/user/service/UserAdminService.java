package com.luna.aggarly.user.service;

import com.luna.aggarly.user.dto.response.UserAdminResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserAdminService {

    Page<UserAdminResponse> getAllUsersAdmin(String role, String status, String search, Pageable pageable);

    UserAdminResponse updateUserStatus(UUID userId, String status, String reason);

    void forcePasswordReset(UUID userId);
}
