package com.luna.aggarly.aiagent.tool.schedule;

import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.aiagent.tool.schedule.record.TaskActionParams;
import com.luna.aggarly.aiagent.tool.schedule.record.TaskActionResponse;
import com.luna.aggarly.scheduler.service.ScheduledTaskService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleCancelTool implements Tool<TaskActionParams, TaskActionResponse> {

    private final ScheduledTaskService scheduledTaskService;
    private final JsonSchemaService jsonSchemaService;

    @Override
    public String name() {
        return "schedule.cancel";
    }

    @Override
    public String description() {
        return "Cancels and deactivates a scheduled task permanently.";
    }

    @Override
    public Class<TaskActionParams> parameterType() {
        return TaskActionParams.class;
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
    public ToolResult<TaskActionResponse> execute(TaskActionParams params, UserPrincipal user) {
        UUID userId = user != null ? user.getUserId() : null;
        if (userId == null) {
            return ToolResult.failed("AUTHENTICATION_REQUIRED", "User must be authenticated to cancel tasks.");
        }

        try {
            scheduledTaskService.cancelTask(params.taskId(), userId);
            return ToolResult.ok(new TaskActionResponse(
                    params.taskId(),
                    "CANCELLED",
                    "Scheduled task has been cancelled successfully."
            ));
        } catch (Exception ex) {
            log.error("Failed to cancel task", ex);
            return ToolResult.failed("SCHEDULE_CANCEL_FAILED", ex.getMessage());
        }
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(TaskActionParams.class);
    }

    @Override
    public JsonNode responseSchema() {
        return jsonSchemaService.generate(TaskActionResponse.class);
    }
}
