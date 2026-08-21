package com.luna.aggarly.aiagent.tool.schedule;

import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.aiagent.tool.schedule.record.ListSchedulesParams;
import com.luna.aggarly.aiagent.tool.schedule.record.ListSchedulesResponse;
import com.luna.aggarly.scheduler.dto.ScheduledTaskSummaryResponse;
import com.luna.aggarly.scheduler.service.ScheduledTaskService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleListTool implements Tool<ListSchedulesParams, ListSchedulesResponse> {

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
    public Class<ListSchedulesParams> parameterType() {
        return ListSchedulesParams.class;
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
    public ToolResult<ListSchedulesResponse> execute(ListSchedulesParams params, UserPrincipal user) {
        UUID userId = user != null ? user.getUserId() : null;
        if (userId == null) {
            return ToolResult.failed("AUTHENTICATION_REQUIRED", "User must be authenticated to list scheduled tasks.");
        }

        int page = params != null && params.page() != null ? params.page() : 0;
        int size = params != null && params.size() != null ? params.size() : 20;

        Page<ScheduledTaskSummaryResponse> tasksPage = scheduledTaskService.listMyTasks(userId, PageRequest.of(page, size));
        ListSchedulesResponse resp = new ListSchedulesResponse(tasksPage.getContent(), tasksPage.getTotalElements());
        return ToolResult.ok(resp);
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(ListSchedulesParams.class);
    }

    @Override
    public JsonNode responseSchema() {
        return jsonSchemaService.generate(ListSchedulesResponse.class);
    }
}
