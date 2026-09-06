package com.luna.aggarly.aiagent.tool.schedule;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.scheduler.dto.ScheduledTaskSummaryResponse;
import com.luna.aggarly.scheduler.service.ScheduledTaskService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleListTool implements Tool<ScheduleListTool.Params, ScheduleListTool.Response> {

    public record Params(
            @JsonPropertyDescription("Page number (0-indexed, default is 0).")
            Integer page,

            @JsonPropertyDescription("Number of tasks to return per page (default is 10, max is 50).")
            Integer size
    ) {}

    public record Response(
            List<ScheduledTaskSummaryResponse> tasks,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext,
            String summary
    ) {}

    private final ScheduledTaskService scheduledTaskService;
    private final JsonSchemaService jsonSchemaService;

    @Override
    public String name() {
        return "schedule.list";
    }

    @Override
    public String description() {
        return "Lists active and past scheduled tasks and automations created by the authenticated user.";
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
            return ToolResult.failed("AUTHENTICATION_REQUIRED", "User must be authenticated to list scheduled tasks.");
        }

        int page = params != null && params.page() != null ? params.page() : 0;
        int size = params != null && params.size() != null ? Math.min(params.size(), 50) : 10;

        try {
            Page<ScheduledTaskSummaryResponse> resultPage = scheduledTaskService.listMyTasks(userId, PageRequest.of(page, size));
            String summary = String.format("Found %d scheduled task(s) for user (page %d of %d).",
                    resultPage.getTotalElements(), resultPage.getNumber() + 1, resultPage.getTotalPages());

            Response response = new Response(
                    resultPage.getContent(),
                    resultPage.getNumber(),
                    resultPage.getSize(),
                    resultPage.getTotalElements(),
                    resultPage.getTotalPages(),
                    resultPage.hasNext(),
                    summary
            );
            return ToolResult.ok(response);
        } catch (Exception ex) {
            log.error("Failed to list scheduled tasks for user {}: {}", userId, ex.getMessage(), ex);
            return ToolResult.failed("LIST_FAILED", "Failed to retrieve scheduled tasks: " + ex.getMessage());
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
