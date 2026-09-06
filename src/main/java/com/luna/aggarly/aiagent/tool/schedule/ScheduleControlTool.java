package com.luna.aggarly.aiagent.tool.schedule;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.scheduler.dto.ScheduledTaskResponse;
import com.luna.aggarly.scheduler.service.ScheduledTaskService;
import com.luna.aggarly.user.security.UserPrincipal;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleControlTool implements Tool<ScheduleControlTool.Params, ScheduleControlTool.Response> {

    public record Params(
            @NotNull
            @JsonPropertyDescription("The UUID of the scheduled task to operate on.")
            UUID taskId,

            @NotNull
            @JsonPropertyDescription("The lifecycle action to apply: PAUSE, RESUME, CANCEL or RUN_NOW.")
            ScheduleAction action
    ) {
        public enum ScheduleAction {
            PAUSE, RESUME, CANCEL, RUN_NOW;

            @JsonCreator
            public static ScheduleAction fromString(String value) {
                if (value == null) return null;
                return ScheduleAction.valueOf(value.trim().toUpperCase().replace(' ', '_'));
            }
        }
    }

    public record Response(
            UUID taskId,
            String action,
            String newStatus,
            String message,
            String nextExecutionAt
    ) {
        public static Response of(ScheduledTaskResponse task, String action, String message) {
            return new Response(
                    task.id(),
                    action,
                    task.status() != null ? task.status().name() : null,
                    message,
                    task.nextExecutionAt() != null ? task.nextExecutionAt().toString() : null
            );
        }
    }

    private final ScheduledTaskService scheduledTaskService;
    private final JsonSchemaService jsonSchemaService;

    @Override
    public String name() {
        return "schedule.control";
    }

    @Override
    public String description() {
        return "Applies a lifecycle action to a scheduled automation task: PAUSE (temporarily stop), " +
                "RESUME (reactivate and recompute next run), CANCEL (permanently deactivate) or RUN_NOW " +
                "(immediate execution without disrupting the regular schedule).";
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
        UUID userId = user != null ? user.getUserId() : null;
        if (userId == null) {
            return ToolResult.failed("AUTHENTICATION_REQUIRED", "User must be authenticated to control tasks.");
        }
        if (params.action() == null) {
            return ToolResult.failed("INVALID_ACTION", "A lifecycle action (PAUSE, RESUME, CANCEL, RUN_NOW) is required.");
        }

        UUID taskId = params.taskId();
        try {
            return switch (params.action()) {
                case PAUSE -> {
                    ScheduledTaskResponse res = scheduledTaskService.pauseTask(taskId, userId);
                    yield ToolResult.ok(Response.of(res, "PAUSE", "Task '" + res.name() + "' paused."));
                }
                case RESUME -> {
                    ScheduledTaskResponse res = scheduledTaskService.resumeTask(taskId, userId);
                    yield ToolResult.ok(Response.of(res, "RESUME", "Task '" + res.name() + "' resumed. Next run: " + res.nextExecutionAt()));
                }
                case CANCEL -> {
                    scheduledTaskService.cancelTask(taskId, userId);
                    ScheduledTaskResponse res = scheduledTaskService.getTaskById(taskId, userId);
                    yield ToolResult.ok(Response.of(res, "CANCEL", "Task '" + (res != null ? res.name() : taskId) + "' cancelled."));
                }
                case RUN_NOW -> {
                    scheduledTaskService.runNow(taskId, userId);
                    ScheduledTaskResponse res = scheduledTaskService.getTaskById(taskId, userId);
                    yield ToolResult.ok(Response.of(res, "RUN_NOW", "Task '" + (res != null ? res.name() : taskId) + "' triggered for immediate execution."));
                }
            };
        } catch (Exception ex) {
            log.error("Failed to apply action {} to task {}: {}", params.action(), taskId, ex.getMessage(), ex);
            return ToolResult.failed("ACTION_FAILED", "Failed to " + params.action() + " task: " + ex.getMessage());
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
