package com.luna.aggarly.user.dto.response;

import com.luna.aggarly.user.entity.UserSession;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record UserSessionResponse(
        UUID id,
        String deviceName,
        String ipAddress,
        String location,
        Instant lastActiveAt,
        Instant createdAt,
        boolean isCurrent
) {
    public static UserSessionResponse fromEntity(UserSession session, boolean isCurrent) {
        return UserSessionResponse.builder()
                .id(session.getId())
                .deviceName(session.getDeviceName() != null ? session.getDeviceName() : "Unknown Device")
                .ipAddress(session.getIpAddress() != null ? session.getIpAddress() : "Unknown IP")
                .location(session.getLocation() != null ? session.getLocation() : "Unknown")
                .lastActiveAt(session.getLastActiveAt())
                .createdAt(session.getCreatedAt())
                .isCurrent(isCurrent)
                .build();
    }
}
