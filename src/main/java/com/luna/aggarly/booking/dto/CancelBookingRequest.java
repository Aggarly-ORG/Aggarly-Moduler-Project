package com.luna.aggarly.booking.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelBookingRequest(
        @NotBlank(message = "Cancellation reason is required")
        String reason
) {
}
