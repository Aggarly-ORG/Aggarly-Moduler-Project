package com.luna.aggarly.scheduler.workflow;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record WorkflowStep(

        @NotBlank
        @JsonPropertyDescription("Unique identifier of this step within the workflow (e.g. 'get_report', 'send_alert').")
        String id,

        @NotBlank
        @JsonPropertyDescription("Type of workflow step. Supported: 'service_call'.")
        String type,

        @NotBlank
        @JsonPropertyDescription("Registered operation name, for example 'host.earningsReport', 'host.upcomingReservations', 'notification.send', 'notification.sendEmail', 'notification.sendBookingNotification'.")
        String service,

        @NotNull
        @JsonPropertyDescription("Arguments passed to the registered operation. Values may contain runtime expressions like {{event.bookingId}}, {{step.previous.result}}, or registered functions.")
        Map<String, Object> arguments,

        @JsonPropertyDescription("Optional timeout in seconds for this step execution.")
        Integer timeoutSeconds
) {
    public WorkflowStep {
        if (type == null || type.isBlank()) {
            type = "service_call";
        }
        if (arguments == null) {
            arguments = Map.of();
        }
        if (timeoutSeconds == null || timeoutSeconds <= 0) {
            timeoutSeconds = 30;
        }
    }
}
