package com.luna.aggarly.aiagent.tool.schedule;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.common.expression.EvaluationMode;
import com.luna.aggarly.common.expression.ExpressionEngine;
import com.luna.aggarly.common.expression.ExpressionValidator;
import com.luna.aggarly.scheduler.dto.plan.TriggerConfig;
import com.luna.aggarly.scheduler.entity.enums.ExecutionType;
import com.luna.aggarly.scheduler.entity.enums.TriggerType;
import com.luna.aggarly.scheduler.workflow.ExecutionContext;
import com.luna.aggarly.scheduler.workflow.WorkflowPlan;
import com.luna.aggarly.scheduler.workflow.WorkflowStep;
import com.luna.aggarly.user.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class SchedulePreviewTool implements Tool<SchedulePreviewTool.Params, SchedulePreviewTool.Response> {

    public record Params(
            @NotBlank
            @JsonPropertyDescription("Human-readable name of the scheduled automation.")
            String name,

            @JsonPropertyDescription("Optional description of what the automation does.")
            String description,

            @NotNull
            @JsonPropertyDescription("How the task is triggered: ONCE, DAILY, WEEKLY, MONTHLY, INTERVAL, EVENT, EVENT_OFFSET.")
            TriggerType triggerType,

            @JsonPropertyDescription("How the workflow is executed. Default: DETERMINISTIC.")
            ExecutionType executionType,

            @JsonPropertyDescription("IANA timezone such as UTC, Europe/London, Africa/Cairo, America/New_York.")
            String timezone,

            @Valid
            @JsonPropertyDescription("Configuration corresponding to the selected trigger type.")
            TriggerConfig triggerConfig,

            @NotNull
            @Valid
            @JsonPropertyDescription("Executable workflow plan containing steps and expressions to preview.")
            WorkflowPlan plan,

            @JsonPropertyDescription("Optional sample context variables or mock event object to evaluate against.")
            Map<String, Object> sampleContext
    ) {}

    public record Response(
            boolean isValid,
            String name,
            String triggerSummary,
            List<String> validationErrors,
            List<Map<String, Object>> simulatedSteps,
            String message
    ) {}

    private final ExpressionEngine expressionEngine;
    private final ExpressionValidator expressionValidator;
    private final JsonSchemaService jsonSchemaService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "schedule.preview";
    }

    @Override
    public String description() {
        return "Simulates and verifies an automation workflow plan, validating all expressions without persisting the task to the database.";
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
        return false;
    }

    @Override
    public ToolResult<Response> execute(Params params, UserPrincipal user) {
        if (params == null || params.plan() == null) {
            return ToolResult.failed("INVALID_PARAMS", "Workflow plan is required for preview.");
        }

        UUID userId = user != null ? user.getUserId() : UUID.randomUUID();
        UUID mockTaskId = UUID.randomUUID();
        UUID mockExecId = UUID.randomUUID();
        String timezone = params.timezone() != null ? params.timezone() : "UTC";

        Map<String, Object> sampleRoot = params.sampleContext() != null ? new HashMap<>(params.sampleContext()) : new HashMap<>();
        ExecutionContext context = ExecutionContext.withRoot(userId, mockTaskId, mockExecId, sampleRoot.get("event"), timezone, sampleRoot);

        List<String> allValidationErrors = new ArrayList<>();
        List<Map<String, Object>> simulatedSteps = new ArrayList<>();

        WorkflowPlan plan = params.plan();
        if (plan.steps() != null) {
            for (WorkflowStep step : plan.steps()) {
                String stepId = step.id();
                String opType = step.service();
                Map<String, Object> rawArgs = step.arguments() != null ? step.arguments() : Map.of();

                // Validate expressions in arguments
                try {
                    String rawArgsJson = objectMapper.writeValueAsString(rawArgs);
                    ExpressionValidator.ValidationResult valResult = expressionValidator.validateTemplate(rawArgsJson);
                    if (!valResult.isValid()) {
                        allValidationErrors.addAll(valResult.errors());
                    }
                } catch (Exception ignored) {}

                // Simulate evaluation
                Object resolvedArgs = expressionEngine.resolve(rawArgs, context, EvaluationMode.LENIENT);

                Map<String, Object> stepSim = new LinkedHashMap<>();
                stepSim.put("stepId", stepId);
                stepSim.put("operation", opType);
                stepSim.put("rawArguments", rawArgs);
                stepSim.put("evaluatedArguments", resolvedArgs);

                // Mock a sample step result for subsequent step resolution
                Map<String, Object> mockResult = Map.of(
                        "status", "SUCCESS",
                        "simulated", true,
                        "sampleId", UUID.randomUUID().toString()
                );
                context.stepResults().put(stepId, mockResult);
                stepSim.put("mockResult", mockResult);

                simulatedSteps.add(stepSim);
            }
        }

        boolean isValid = allValidationErrors.isEmpty();
        String triggerSummary = params.triggerType() + (params.triggerConfig() != null ? " (" + params.triggerConfig().getClass().getSimpleName() + ")" : "");
        String message = isValid
                ? "Workflow plan validated successfully. Expressions resolved without errors."
                : "Validation failed with " + allValidationErrors.size() + " issue(s).";

        Response response = new Response(
                isValid,
                params.name(),
                triggerSummary,
                allValidationErrors,
                simulatedSteps,
                message
        );

        return ToolResult.ok(response);
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
