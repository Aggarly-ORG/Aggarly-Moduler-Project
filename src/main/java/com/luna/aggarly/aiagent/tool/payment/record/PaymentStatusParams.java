package com.luna.aggarly.aiagent.tool.payment.record;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PaymentStatusParams(
        @NotNull
        @JsonPropertyDescription("The unique identifier (UUID) of the booking whose payment status is being queried.")
        UUID bookingId
) {}
