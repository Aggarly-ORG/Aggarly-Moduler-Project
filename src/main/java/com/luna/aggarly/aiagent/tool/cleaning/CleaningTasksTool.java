package com.luna.aggarly.aiagent.tool.cleaning;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.cleaning.dto.response.CleaningTaskSummaryResponse;
import com.luna.aggarly.cleaning.entity.enums.CleaningStatus;
import com.luna.aggarly.cleaning.service.CleaningTaskService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CleaningTasksTool implements Tool<CleaningTasksTool.Params, Map<String, Object>> {

    private final CleaningTaskService cleaningTaskService;
    private final JsonSchemaService jsonSchemaService;

    public record Params(
            @JsonPropertyDescription("Optional property UUID to filter tasks for one listing.")
            UUID propertyId,

            @JsonPropertyDescription("Optional status filter: SCHEDULED, IN_PROGRESS, COMPLETED, INSPECTED, SKIPPED or CANCELLED.")
            String status
    ) {}

    @Override
    public String name() {
        return "cleaning.tasks";
    }

    @Override
    public String description() {
        return "List turnover cleaning tasks for the authenticated host, optionally filtered by property and status " +
                "(SCHEDULED, IN_PROGRESS, COMPLETED, INSPECTED, SKIPPED, CANCELLED). Read-only overview of upcoming " +
                "and past cleanings with schedules, priorities and issue counts.";
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
    public ToolResult<Map<String, Object>> execute(Params params, UserPrincipal user) {
        if (user == null || user.getUserId() == null) {
            return ToolResult.failed("AUTH_REQUIRED", "User must be authenticated to view cleaning tasks.");
        }

        try {
            CleaningStatus status = null;
            if (params.status() != null && !params.status().isBlank()) {
                status = CleaningStatus.valueOf(params.status().trim().toUpperCase());
            }

            var page = cleaningTaskService.getHostTasks(
                    user.getUserId(),
                    status,
                    params.propertyId(),
                    PageRequest.of(0, 25)
            );

            List<Map<String, Object>> tasks = page.getContent().stream()
                    .map(this::toMap)
                    .toList();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("totalAvailable", page.getTotalElements());
            result.put("returned", tasks.size());
            result.put("tasks", tasks);
            return ToolResult.ok(result);
        } catch (IllegalArgumentException ex) {
            return ToolResult.failed("INVALID_STATUS_FILTER",
                    "Unknown cleaning status '" + params.status() + "'.");
        } catch (Exception ex) {
            log.error("Failed to list cleaning tasks for host {}", user.getUserId(), ex);
            return ToolResult.failed("CLEANING_TASKS_FETCH_FAILED", ex.getMessage());
        }
    }

    private Map<String, Object> toMap(CleaningTaskSummaryResponse t) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("taskId", t.id());
        map.put("propertyId", t.propertyId());
        map.put("bookingId", t.bookingId());
        map.put("status", t.status() != null ? t.status().name() : null);
        map.put("priority", t.priority() != null ? t.priority().name() : null);
        map.put("taskType", t.taskType() != null ? t.taskType().name() : null);
        map.put("scheduledDate", t.scheduledDate());
        map.put("scheduledStartTime", t.scheduledStartTime());
        map.put("estimatedDurationMinutes", t.estimatedDurationMinutes());
        map.put("assignedCleanerId", t.assignedCleanerId());
        map.put("checklistRoomsCount", t.checklistRoomsCount());
        map.put("issuesCount", t.issuesCount());
        map.put("photosCount", t.photosCount());
        map.put("hostRating", t.ratingByHost());
        return map;
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(Params.class);
    }
}
