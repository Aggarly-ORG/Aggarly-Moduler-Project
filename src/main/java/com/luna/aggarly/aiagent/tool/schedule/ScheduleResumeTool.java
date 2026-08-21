package com.luna.aggarly.aiagent.tool.schedule;

import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.aiagent.tool.schedule.record.TaskActionParams;
import com.luna.aggarly.aiagent.tool.schedule.record.TaskActionResponse;
import com.luna.aggarly.scheduler.dto.ScheduledTaskResponse;
import com.luna.aggarly.scheduler.service.ScheduledTaskService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleResumeTool implements Tool<TaskActionParams, TaskActionResponse> {

    private final ScheduledTaskService scheduledTaskService;
    private final JsonSchemaService jsonSchemaService;

    @Override
    public String name() {
        return "schedule.resume";
    }

    @Override
    public String description() {
        return "Resumes a paused scheduled automation task and recalculates its next run time.";
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
            return ToolResult.failed("AUTHENTICATION_REQUIRED", "User must be authenticated to resume tasks.");
        }

        try {
            ScheduledTaskResponse resp = scheduledTaskService.resumeTask(params.taskId(), userId);
            return ToolResult.ok(new TaskActionResponse(
                    resp.id(),
                    resp.status().name(),
                    String.format("Scheduled task '%s' has been resumed. Next execution: %s",
                            resp.name(), resp.nextExecutionAt() != null ? resp.nextExecutionAt() : "Active")
            ));
        } catch (Exception ex) {
            log.error("Failed to resume task", ex);
            return ToolResult.failed("SCHEDULE_RESUME_FAILED", ex.getMessage());
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
