package com.luna.aggarly.scheduler.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.scheduler.function.FunctionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowValueResolver {

    private static final Pattern SINGLE_EXPRESSION_PATTERN = Pattern.compile("^\\{\\{\\s*(.+?)\\s*\\}\\}$");
    private static final Pattern EMBEDDED_EXPRESSION_PATTERN = Pattern.compile("\\{\\{\\s*(.+?)\\s*\\}\\}");

    private final FunctionRegistry functionRegistry;
    private final ObjectMapper objectMapper;

    /**
     * Recursively resolves dynamic expressions in strings, lists, and maps.
     */
    public Object resolve(Object value, ExecutionContext context) {
        if (value == null) {
            return null;
        }

        if (value instanceof String str) {
            return resolveString(str, context);
        }

        if (value instanceof Map<?, ?> map) {
            Map<String, Object> resolvedMap = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                resolvedMap.put(key, resolve(entry.getValue(), context));
            }
            return resolvedMap;
        }

        if (value instanceof List<?> list) {
            List<Object> resolvedList = new ArrayList<>();
            for (Object item : list) {
                resolvedList.add(resolve(item, context));
            }
            return resolvedList;
        }

        return value;
    }

    private Object resolveString(String input, ExecutionContext context) {
        if (input == null || input.isBlank()) {
            return input;
        }

        // Check if the entire string is a single expression (preserve typed return value)
        Matcher singleMatcher = SINGLE_EXPRESSION_PATTERN.matcher(input.trim());
        if (singleMatcher.matches()) {
            String expr = singleMatcher.group(1).trim();
            return evaluateExpression(expr, context);
        }

        // Interpolated string containing one or more expressions
        Matcher embeddedMatcher = EMBEDDED_EXPRESSION_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (embeddedMatcher.find()) {
            String expr = embeddedMatcher.group(1).trim();
            Object evaluated = evaluateExpression(expr, context);
            String replacement = evaluated != null ? evaluated.toString() : "";
            embeddedMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        embeddedMatcher.appendTail(sb);
        return sb.toString();
    }

    private Object evaluateExpression(String expr, ExecutionContext context) {
        try {
            if ("obj".equals(expr)) {
                return context.rootObject();
            }
            if (expr.startsWith("obj.")) {
                return resolveObjReference(expr.substring(4), context);
            }
            if (expr.startsWith("step.")) {
                return resolveStepReference(expr.substring(5), context);
            }
            if (expr.startsWith("event.")) {
                return resolveEventReference(expr.substring(6), context);
            }
            if (expr.startsWith("function.")) {
                return resolveFunctionReference(expr.substring(9), context);
            }
            if (expr.startsWith("trigger.") || expr.startsWith("plan.") || expr.startsWith("task.") || expr.startsWith("user.")) {
                return resolveObjReference(expr, context);
            }
            log.warn("Unrecognized workflow expression: {}", expr);
            return null;
        } catch (Exception ex) {
            log.error("Failed to evaluate expression '{}'", expr, ex);
            return null;
        }
    }

    private Object resolveObjReference(String propertyPath, ExecutionContext context) {
        Map<String, Object> root = context.rootObject();
        if (root == null || root.isEmpty()) {
            log.warn("ExecutionContext contains empty rootObject while evaluating 'obj.{}'", propertyPath);
            return null;
        }
        return extractNestedProperty(root, propertyPath);
    }

    private Object resolveStepReference(String path, ExecutionContext context) {
        int firstDot = path.indexOf('.');
        String stepId = firstDot >= 0 ? path.substring(0, firstDot) : path;
        String propertyPath = firstDot >= 0 ? path.substring(firstDot + 1) : "";

        Object stepResult = context.stepResults().get(stepId);
        if (stepResult == null) {
            log.warn("Referenced step '{}' returned no result or hasn't run yet.", stepId);
            return null;
        }

        if (propertyPath.isBlank() || propertyPath.equals("result")) {
            return stepResult;
        }

        if (propertyPath.startsWith("result.")) {
            propertyPath = propertyPath.substring(7);
        }

        return extractNestedProperty(stepResult, propertyPath);
    }

    private Object resolveEventReference(String propertyPath, ExecutionContext context) {
        Object event = context.event();
        if (event == null) {
            // Fallback to obj.event if available
            if (context.rootObject() != null && context.rootObject().containsKey("event")) {
                event = context.rootObject().get("event");
            }
        }
        if (event == null) {
            log.warn("Workflow context contains no event object.");
            return null;
        }
        return extractNestedProperty(event, propertyPath);
    }

    private Object resolveFunctionReference(String expr, ExecutionContext context) {
        String fnName = expr;
        List<Object> args = new ArrayList<>();

        int parenStart = expr.indexOf('(');
        if (parenStart >= 0 && expr.endsWith(")")) {
            fnName = expr.substring(0, parenStart).trim();
            String argsStr = expr.substring(parenStart + 1, expr.length() - 1).trim();
            if (!argsStr.isBlank()) {
                String[] rawArgs = argsStr.split(",");
                for (String rawArg : rawArgs) {
                    args.add(rawArg.trim().replaceAll("^\"|\"$|^'|'$", ""));
                }
            }
        }

        return functionRegistry.execute(fnName, args, context);
    }

    private Object extractNestedProperty(Object root, String propertyPath) {
        if (root == null || propertyPath == null || propertyPath.isBlank()) {
            return root;
        }

        try {
            JsonNode rootNode = (root instanceof JsonNode jn) ? jn : objectMapper.valueToTree(root);
            String[] parts = propertyPath.split("\\.");
            JsonNode current = rootNode;

            for (String part : parts) {
                if (current == null || current.isMissingNode() || current.isNull()) {
                    return null;
                }

                // Support numeric array index access, e.g. "steps.0.id" or "items.1"
                if (current.isArray()) {
                    try {
                        int index = Integer.parseInt(part);
                        current = (index >= 0 && index < current.size()) ? current.get(index) : null;
                        continue;
                    } catch (NumberFormatException ignored) {}
                }

                current = current.get(part);
            }

            if (current == null || current.isMissingNode() || current.isNull()) {
                return null;
            }

            if (current.isTextual()) return current.asText();
            if (current.isNumber()) return current.numberValue();
            if (current.isBoolean()) return current.asBoolean();
            return objectMapper.treeToValue(current, Object.class);

        } catch (Exception ex) {
            log.warn("Failed to extract property '{}' from object: {}", propertyPath, ex.getMessage());
            return null;
        }
    }
}
