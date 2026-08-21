package com.luna.aggarly.user.dto.request;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record Mfa(
        UUID userId,

        Instant createdAt
) implements Serializable {
}