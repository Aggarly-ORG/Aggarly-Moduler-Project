package com.luna.aggarly.aiagent.tool.schedule.record;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.luna.aggarly.scheduler.dto.plan.TriggerConfig;
import com.luna.aggarly.scheduler.entity.enums.ExecutionType;
import com.luna.aggarly.scheduler.entity.enums.TriggerType;
import com.luna.aggarly.scheduler.workflow.WorkflowPlan;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateScheduleParams(

        @NotBlank
        @JsonPropertyDescription("Human-readable name of the scheduled automation.")
        String name,

        @JsonPropertyDescription("Optional description of what the automation does.")
        String description,

        @NotNull
        @JsonPropertyDescription("How the task is triggered: ONCE, DAILY, WEEKLY, MONTHLY, INTERVAL, EVENT, EVENT_OFFSET.")
        TriggerType triggerType,

        @NotNull
        @JsonPropertyDescription("How the workflow is executed. DETERMINISTIC is preferred whenever the workflow can be represented by registered operations.")
        ExecutionType executionType,

        @NotBlank
        @JsonPropertyDescription("IANA timezone such as UTC, Europe/London, Africa/Cairo, America/New_York.")
        String timezone,

        @NotNull
        @Valid
        @JsonPropertyDescription("Configuration corresponding to the selected trigger type.")
        TriggerConfig triggerConfig,

        @NotNull
        @Valid
        @JsonPropertyDescription("Executable workflow plan.")
        WorkflowPlan plan
) {}
