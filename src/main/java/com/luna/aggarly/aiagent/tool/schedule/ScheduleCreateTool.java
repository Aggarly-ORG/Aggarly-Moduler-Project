package com.luna.aggarly.aiagent.tool.schedule;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.scheduler.dto.CreateScheduledTaskRequest;
import com.luna.aggarly.scheduler.dto.ScheduledTaskResponse;
import com.luna.aggarly.scheduler.dto.plan.*;
import com.luna.aggarly.scheduler.entity.enums.ExecutionType;
import com.luna.aggarly.scheduler.entity.enums.TriggerType;
import com.luna.aggarly.scheduler.service.ScheduledTaskService;
import com.luna.aggarly.scheduler.workflow.WorkflowPlan;
import com.luna.aggarly.user.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleCreateTool implements Tool<ScheduleCreateTool.Params, ScheduleCreateTool.Response> {

    public record Params(
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

    public record Response(
            UUID taskId,
            String name,
            String status,
            String triggerType,
            String nextExecutionAt,
            int stepsCount,
            String summary
    ) {
        public static Response fromScheduledTask(ScheduledTaskResponse task, int stepsCount) {
            String nextRun = task.nextExecutionAt() != null ? task.nextExecutionAt().toString() : "On Event";
            String summary = String.format("Automation '%s' successfully scheduled with %d step(s). Next execution: %s.",
                    task.name(), stepsCount, nextRun);
            return new Response(
                    task.id(),
                    task.name(),
                    task.status() != null ? task.status().name() : "ACTIVE",
                    task.triggerType() != null ? task.triggerType().name() : "UNKNOWN",
                    task.nextExecutionAt() != null ? task.nextExecutionAt().toString() : null,
                    stepsCount,
                    summary
            );
        }
    }

    private final ScheduledTaskService scheduledTaskService;
    private final JsonSchemaService jsonSchemaService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "schedule.create";
    }

    @Override
    public String description() {
        return "Compiles a natural language request into a validated, persistent scheduled automation task in PostgreSQL.";
    }

    @Override
    public Class<Params> parameterType() {
        return Params.class;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public ToolResult<Response> execute(Params params, UserPrincipal user) {
        try {
            TriggerType triggerType = params.triggerType();
            TriggerConfig triggerConfig = params.triggerConfig();
            validateTriggerConfig(triggerType, triggerConfig);

            Map<String, Object> triggerConfigMap = triggerConfig != null ? objectMapper.convertValue(triggerConfig, Map.class) : Map.of();
            Map<String, Object> planMap = params.plan() != null ? objectMapper.convertValue(params.plan(), Map.class) : Map.of();

            CreateScheduledTaskRequest request = CreateScheduledTaskRequest.builder()
                    .name(params.name())
                    .description(params.description())
                    .triggerType(triggerType)
                    .executionType(params.executionType() != null ? params.executionType() : ExecutionType.DETERMINISTIC)
                    .misfirePolicy(com.luna.aggarly.scheduler.entity.enums.MisfirePolicy.RUN_ONCE_NOW)
                    .timezone(params.timezone() != null ? params.timezone() : "UTC")
                    .triggerConfig(triggerConfigMap)
                    .plan(planMap)
                    .maxRetries(3)
                    .build();

            ScheduledTaskResponse task = scheduledTaskService.createTask(request, user);

            int stepsCount = (params.plan() != null && params.plan().steps() != null)
                    ? params.plan().steps().size()
                    : 0;

            Response response = Response.fromScheduledTask(task, stepsCount);
            return ToolResult.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid schedule parameters: {}", e.getMessage());
            return ToolResult.failed("INVALID_SCHEDULE_PARAMS", e.getMessage());
        } catch (Exception e) {
            log.error("Failed to create scheduled automation: {}", e.getMessage(), e);
            return ToolResult.failed("SCHEDULE_CREATION_FAILED",
                    "Failed to register scheduled automation: " + e.getMessage());
        }
    }

    private void validateTriggerConfig(TriggerType type, TriggerConfig config) {
        if (type == null || config == null) {
            throw new IllegalArgumentException("Both triggerType and triggerConfig are required.");
        }

        switch (type) {
            case ONCE -> {
                if (!(config instanceof OnceTriggerConfig)) {
                    throw new IllegalArgumentException("ONCE trigger requires OnceTriggerConfig with 'executeAt'");
                }
            }
            case DAILY -> {
                if (!(config instanceof DailyTriggerConfig)) {
                    throw new IllegalArgumentException("DAILY trigger requires DailyTriggerConfig with 'time'");
                }
            }
            case WEEKLY -> {
                if (!(config instanceof WeeklyTriggerConfig w) || w.days() == null || w.days().isEmpty()) {
                    throw new IllegalArgumentException("WEEKLY trigger requires WeeklyTriggerConfig with non-empty 'days'");
                }
            }
            case MONTHLY -> {
                if (!(config instanceof MonthlyTriggerConfig m) || m.dayOfMonth() < 1 || m.dayOfMonth() > 31) {
                    throw new IllegalArgumentException("MONTHLY trigger requires MonthlyTriggerConfig with dayOfMonth between 1 and 31");
                }
            }
            case INTERVAL -> {
                if (!(config instanceof IntervalTriggerConfig i) || i.every() <= 0 || i.unit() == null) {
                    throw new IllegalArgumentException("INTERVAL trigger requires IntervalTriggerConfig with positive 'every' and valid 'unit'");
                }
            }
            case EVENT -> {
                if (!(config instanceof EventTriggerConfig e) || e.event() == null || e.event().isBlank()) {
                    throw new IllegalArgumentException("EVENT trigger requires EventTriggerConfig with 'event' name");
                }
            }
            case EVENT_OFFSET -> {
                if (!(config instanceof EventOffsetTriggerConfig eo) || eo.event() == null || eo.offset() == null) {
                    throw new IllegalArgumentException("EVENT_OFFSET trigger requires EventOffsetTriggerConfig with 'event' and 'offset'");
                }
            }
        }
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(Params.class);
    }

    @Override
    public JsonNode responseSchema() {
        return jsonSchemaService.generate(Response.class);
    }
}
