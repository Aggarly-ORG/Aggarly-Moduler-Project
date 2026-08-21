package com.luna.aggarly.scheduler.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.scheduler.entity.enums.TriggerType;
import com.luna.aggarly.scheduler.function.FunctionRegistry;
import com.luna.aggarly.scheduler.operation.OperationRegistry;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlanValidator {

    private static final int MAX_STEPS_PER_PLAN = 10;
    private static final Pattern FUNCTION_PATTERN = Pattern.compile("function\\.([a-zA-Z0-9_.]+)");

    private final OperationRegistry operationRegistry;
    private final FunctionRegistry functionRegistry;
    private final ObjectMapper objectMapper;

    public void validatePlan(WorkflowPlan plan, UserPrincipal user) {
        if (plan == null || plan.steps() == null || plan.steps().isEmpty()) {
            throw new IllegalArgumentException("Workflow plan must contain at least one step.");
        }

        if (plan.steps().size() > MAX_STEPS_PER_PLAN) {
            throw new IllegalArgumentException(
                    String.format("Workflow plan exceeds maximum allowable steps limit (%d).", MAX_STEPS_PER_PLAN)
            );
        }

        Set<String> declaredStepIds = new HashSet<>();

        for (int i = 0; i < plan.steps().size(); i++) {
            WorkflowStep step = plan.steps().get(i);

            if (step.id() == null || step.id().isBlank()) {
                throw new IllegalArgumentException(String.format("Step at index %d has no id.", i));
            }

            if (declaredStepIds.contains(step.id())) {
                throw new IllegalArgumentException(String.format("Duplicate step id '%s' found in plan.", step.id()));
            }

            // 1. Operation Allowlist Check
            if (!operationRegistry.hasOperation(step.service())) {
                throw new IllegalArgumentException(
                        String.format("Operation '%s' in step '%s' is not registered in safe operation allowlist.",
                                step.service(), step.id())
                );
            }

            // 2. Role-based Operation Authorization
            if (step.service().startsWith("host.") && (user == null || !hasAuthority(user, "ROLE_HOST", "ROLE_ADMIN"))) {
                throw new SecurityException(
                        String.format("User is not authorized to schedule host operation '%s'.", step.service())
                );
            }
            if (step.service().startsWith("admin.") && (user == null || !hasAuthority(user, "ROLE_ADMIN"))) {
                throw new SecurityException(
                        String.format("User is not authorized to schedule admin operation '%s'.", step.service())
                );
            }

            // 3. Step Reference Integrity (No forward references)
            validateStepReferences(step.arguments(), declaredStepIds);

            // 4. Function Allowlist Check
            validateFunctionReferences(step.arguments());

            declaredStepIds.add(step.id());
        }
    }

    public void validateTrigger(TriggerType triggerType, String triggerConfigJson, String timezoneStr) {
        if (triggerType == null) {
            throw new IllegalArgumentException("Trigger type cannot be null.");
        }

        if (timezoneStr != null && !timezoneStr.isBlank()) {
            try {
                ZoneId.of(timezoneStr);
            } catch (Exception ex) {
                throw new IllegalArgumentException("Invalid IANA timezone: " + timezoneStr);
            }
        }

        if (triggerConfigJson == null || triggerConfigJson.isBlank()) {
            return;
        }

        try {
            JsonNode config = objectMapper.readTree(triggerConfigJson);

            if (triggerType == TriggerType.INTERVAL) {
                int every = config.path("every").asInt(0);
                String unit = config.path("unit").asText("DAYS").toUpperCase();
                if (every <= 0) {
                    throw new IllegalArgumentException("Interval 'every' must be greater than 0.");
                }
                if (unit.equals("MINUTES") && every < 5) {
                    throw new IllegalArgumentException("Minimum allowable interval is 5 MINUTES.");
                }
            } else if (triggerType == TriggerType.MONTHLY) {
                int dayOfMonth = config.path("dayOfMonth").asInt(1);
                if (dayOfMonth < 1 || dayOfMonth > 31) {
                    throw new IllegalArgumentException("Monthly dayOfMonth must be between 1 and 31.");
                }
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid triggerConfig JSON: " + ex.getMessage());
        }
    }

    private void validateStepReferences(Object arguments, Set<String> validPrecedingStepIds) {
        if (arguments == null) return;
        String argsString = arguments.toString();

        Pattern stepPattern = Pattern.compile("(?:step|obj\\.step|obj\\.steps)\\.([a-zA-Z0-9_-]+)\\.");
        Matcher matcher = stepPattern.matcher(argsString);
        while (matcher.find()) {
            String referencedStepId = matcher.group(1);
            if (!validPrecedingStepIds.contains(referencedStepId)) {
                throw new IllegalArgumentException(
                        String.format("Invalid step reference '{{step.%s.*}}'. Step '%s' must execute before being referenced.",
                                referencedStepId, referencedStepId)
                );
            }
        }
    }

    private void validateFunctionReferences(Object arguments) {
        if (arguments == null) return;
        String argsString = arguments.toString();

        Matcher matcher = FUNCTION_PATTERN.matcher(argsString);
        while (matcher.find()) {
            String fnName = matcher.group(1);
            int parenIdx = fnName.indexOf('(');
            if (parenIdx >= 0) {
                fnName = fnName.substring(0, parenIdx);
            }
            if (!functionRegistry.hasFunction(fnName)) {
                throw new IllegalArgumentException(
                        String.format("Function '{{function.%s}}' is not registered in safe function allowlist.", fnName)
                );
            }
        }
    }

    private boolean hasAuthority(UserPrincipal user, String... requiredRoles) {
        if (user == null || user.getAuthorities() == null) return false;
        Set<String> authorities = new HashSet<>();
        user.getAuthorities().forEach(a -> authorities.add(a.getAuthority()));
        for (String role : requiredRoles) {
            if (authorities.contains(role)) return true;
        }
        return false;
    }
}
