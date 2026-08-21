package com.luna.aggarly.user.dto.request;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record MfaConfirmation(UUID userId, String secret, Instant CreatedAt) implements Serializable {
}
