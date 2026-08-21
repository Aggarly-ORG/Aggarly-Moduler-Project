package com.luna.aggarly.booking.dto;

import java.time.Instant;

public record BookingStatusHistoryResponse(
        String fromStatus,
        String toStatus,
        String reason,
        Instant createdAt
) {
}
