package com.luna.aggarly.scheduler.workflow;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record WorkflowPlan(

        @NotEmpty
        @Valid
        @JsonPropertyDescription("Ordered workflow steps executed sequentially by the scheduler.")
        List<WorkflowStep> steps,

        @JsonPropertyDescription("Plan schema version (default 1).")
        Integer planVersion,

        @JsonPropertyDescription("Optional error handling policy when a step fails: 'ABORT' (default) or 'CONTINUE'.")
        String fallbackPolicy
) {
    public WorkflowPlan {
        if (steps == null) {
            steps = List.of();
        }
        if (planVersion == null) {
            planVersion = 1;
        }
    }
}
