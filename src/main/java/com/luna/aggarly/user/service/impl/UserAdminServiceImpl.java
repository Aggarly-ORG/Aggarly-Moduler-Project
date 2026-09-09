package com.luna.aggarly.user.service.impl;

import com.luna.aggarly.user.dto.request.ForgotPasswordRequest;
import com.luna.aggarly.user.dto.response.UserAdminResponse;
import com.luna.aggarly.user.entity.Role;
import com.luna.aggarly.user.entity.User;
import com.luna.aggarly.user.exceptions.UserNotFoundException;
import com.luna.aggarly.user.mapper.UserMapper;
import com.luna.aggarly.user.repository.UserRepository;
import com.luna.aggarly.user.service.AuthService;
import com.luna.aggarly.user.service.UserAdminService;
import com.luna.aggarly.user.service.UserSessionService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAdminServiceImpl implements UserAdminService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final UserSessionService userSessionService;
    private final AuthService authService;

    @Override
    @Transactional(readOnly = true)
    public Page<UserAdminResponse> getAllUsersAdmin(String role, String status, String search, Pageable pageable) {
        log.info("Fetching admin paginated users list, role={}, status={}, search={}", role, status, search);

        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("status")), status.trim().toUpperCase()));
            }

            if (role != null && !role.isBlank()) {
                Join<User, Role> roleJoin = root.join("roles");
                predicates.add(cb.equal(cb.upper(roleJoin.get("name")), role.trim().toUpperCase()));
            }

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                Predicate emailMatch = cb.like(cb.lower(root.get("email")), pattern);
                Predicate usernameMatch = cb.like(cb.lower(root.get("username")), pattern);
                Predicate firstMatch = cb.like(cb.lower(root.get("firstName")), pattern);
                Predicate lastMatch = cb.like(cb.lower(root.get("lastName")), pattern);
                Predicate displayMatch = cb.like(cb.lower(root.get("displayName")), pattern);
                predicates.add(cb.or(emailMatch, usernameMatch, firstMatch, lastMatch, displayMatch));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return userRepository.findAll(spec, pageable).map(userMapper::toAdminResponse);
    }

    @Override
    @Transactional
    public UserAdminResponse updateUserStatus(UUID userId, String status, String reason) {
        log.info("Updating user status for userId={}, status={}, reason={}", userId, status, reason);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));

        user.setStatus(status.toUpperCase());
        User saved = userRepository.save(user);

        if ("SUSPENDED".equalsIgnoreCase(status)) {
            log.info("User {} suspended. Terminating all active sessions.", userId);
            userSessionService.revokeAllSessions(userId);
        }

        return userMapper.toAdminResponse(saved);
    }

    @Override
    @Transactional
    public void forcePasswordReset(UUID userId) {
        log.info("Triggering admin forced password reset challenge for userId={}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));

        // Revoke all active sessions
        userSessionService.revokeAllSessions(userId);

        // Dispatch password reset email challenge
        authService.forgotPassword(new ForgotPasswordRequest(user.getEmail()));
    }
}
