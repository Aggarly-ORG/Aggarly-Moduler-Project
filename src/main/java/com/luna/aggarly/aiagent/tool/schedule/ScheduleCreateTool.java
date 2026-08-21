package com.luna.aggarly.aiagent.tool.schedule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.aiagent.tool.schedule.record.CreateScheduleParams;
import com.luna.aggarly.aiagent.tool.schedule.record.CreateScheduleResponse;
import com.luna.aggarly.scheduler.dto.CreateScheduledTaskRequest;
import com.luna.aggarly.scheduler.dto.ScheduledTaskResponse;
import com.luna.aggarly.scheduler.dto.plan.*;
import com.luna.aggarly.scheduler.entity.enums.ExecutionType;
import com.luna.aggarly.scheduler.entity.enums.TriggerType;
import com.luna.aggarly.scheduler.service.ScheduledTaskService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleCreateTool implements Tool<CreateScheduleParams, CreateScheduleResponse> {

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
    public Class<CreateScheduleParams> parameterType() {
        return CreateScheduleParams.class;
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
    public ToolResult<CreateScheduleResponse> execute(CreateScheduleParams params, UserPrincipal user) {
        try {
            TriggerType triggerType = params.triggerType();
            TriggerConfig triggerConfig = params.triggerConfig();

            // Validate trigger type vs config coherence
            validateTriggerCompatibility(triggerType, triggerConfig);

            @SuppressWarnings("unchecked")
            Map<String, Object> configMap = objectMapper.convertValue(triggerConfig, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> planMap = objectMapper.convertValue(params.plan(), Map.class);

            CreateScheduledTaskRequest request = CreateScheduledTaskRequest.builder()
                    .name(params.name())
                    .description(params.description())
                    .triggerType(triggerType)
                    .executionType(params.executionType() != null ? params.executionType() : ExecutionType.DETERMINISTIC)
                    .timezone(params.timezone() != null && !params.timezone().isBlank() ? params.timezone() : "UTC")
                    .triggerConfig(configMap)
                    .plan(planMap)
                    .build();

            ScheduledTaskResponse response = scheduledTaskService.createTask(request, user);

            String nextRun;
            if (response.nextExecutionAt() != null) {
                nextRun = response.nextExecutionAt().toString();
            } else if (triggerType == TriggerType.ONCE) {
                if (triggerConfig instanceof OnceTriggerConfig onceConfig && onceConfig.executeAt() != null) {
                    nextRun = onceConfig.executeAt().toString();
                } else {
                    nextRun = "One-time Execution";
                }
            } else if (triggerType == TriggerType.EVENT || triggerType == TriggerType.EVENT_OFFSET) {
                String eventName = (triggerConfig instanceof EventTriggerConfig ec) ? ec.event()
                        : ((triggerConfig instanceof EventOffsetTriggerConfig eoc) ? eoc.event() : "Application Event");
                nextRun = "On Event: " + eventName;
            } else {
                nextRun = "Scheduled";
            }

            CreateScheduleResponse resp = new CreateScheduleResponse(
                    response.id(),
                    response.name(),
                    response.status().name(),
                    response.triggerType().name(),
                    nextRun,
                    String.format("Scheduled task '%s' created successfully.", response.name())
            );

            return ToolResult.ok(resp);
        } catch (Exception ex) {
            log.error("Failed to create schedule via tool", ex);
            return ToolResult.failed("SCHEDULE_CREATION_FAILED", ex.getMessage());
        }
    }

    private void validateTriggerCompatibility(TriggerType type, TriggerConfig config) {
        if (type == null || config == null) {
            return;
        }

        boolean valid = switch (type) {
            case ONCE -> config instanceof OnceTriggerConfig;
            case DAILY -> config instanceof DailyTriggerConfig;
            case WEEKLY -> config instanceof WeeklyTriggerConfig;
            case MONTHLY -> config instanceof MonthlyTriggerConfig;
            case INTERVAL -> config instanceof IntervalTriggerConfig;
            case EVENT -> config instanceof EventTriggerConfig;
            case EVENT_OFFSET -> config instanceof EventOffsetTriggerConfig;
        };

        if (!valid) {
            throw new IllegalArgumentException(String.format(
                    "Mismatched trigger configuration: triggerType '%s' is not compatible with config class '%s'",
                    type, config.getClass().getSimpleName()
            ));
        }
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(CreateScheduleParams.class);
    }

    @Override
    public JsonNode responseSchema() {
        return jsonSchemaService.generate(CreateScheduleResponse.class);
    }
}
