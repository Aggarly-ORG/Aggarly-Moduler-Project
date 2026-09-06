package com.luna.aggarly.common.expression;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpressionValidator {

    private static final Pattern EXPR_PATTERN = Pattern.compile("(?:\\$?\\{\\{\\s*(.+?)\\s*\\}\\}|\\$\\{\\s*(.+?)\\s*\\})");
    private final ExpressionEngine expressionEngine;

    public record ValidationResult(boolean isValid, List<String> errors) {
        public static ValidationResult valid() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult invalid(List<String> errors) {
            return new ValidationResult(false, errors);
        }
    }

    public ValidationResult validateTemplate(String template) {
        if (template == null || template.isBlank()) {
            return ValidationResult.valid();
        }

        List<String> errors = new ArrayList<>();
        Set<String> allowedNamespaces = expressionEngine.getAvailableNamespaces();
        Set<String> allowedFunctions = expressionEngine.getAvailableFunctions();

        Matcher matcher = EXPR_PATTERN.matcher(template);
        while (matcher.find()) {
            String expr = matcher.group(1) != null ? matcher.group(1).trim() : matcher.group(2).trim();
            validateExpression(expr, allowedNamespaces, allowedFunctions, errors);
        }

        if (errors.isEmpty()) {
            return ValidationResult.valid();
        }
        return ValidationResult.invalid(errors);
    }

    private void validateExpression(String expr, Set<String> allowedNamespaces, Set<String> allowedFunctions, List<String> errors) {
        if (expr == null || expr.isBlank()) return;

        String trimmed = expr.trim();

        // Skip literals (strings, numbers, booleans, null)
        if (Tokenizer.isQuoted(trimmed) || "true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed) || "null".equalsIgnoreCase(trimmed)) {
            return;
        }
        if (trimmed.matches("^-?\\d+(\\.\\d+)?$")) {
            return;
        }

        // Function call check
        int parenIdx = trimmed.indexOf('(');
        if (parenIdx > 0 && trimmed.endsWith(")")) {
            String fnName = trimmed.substring(0, parenIdx).trim().toLowerCase();
            if (!allowedFunctions.contains(fnName) && !isBuiltInFn(fnName)) {
                errors.add("Unknown expression function '" + fnName + "'. Available functions: " + allowedFunctions);
            }
            String argsStr = expr.substring(parenIdx + 1, expr.length() - 1);
            List<String> args = Tokenizer.splitArguments(argsStr);
            for (String arg : args) {
                validateExpression(arg.trim(), allowedNamespaces, allowedFunctions, errors);
            }
            return;
        }

        // Path check: "step.search.result[0].id"
        String normalized = expr.replaceAll("\\[(\\d+)\\]", ".$1");
        String[] parts = normalized.split("\\.");
        if (parts.length > 0) {
            String root = parts[0].toLowerCase();
            if (!allowedNamespaces.contains(root) && !isStandardKeyword(root)) {
                errors.add("Unknown expression namespace or variable root '" + root + "'. Available namespaces: " + allowedNamespaces);
            }
        }
    }

    private boolean isBuiltInFn(String fnName) {
        return fnName.startsWith("dateutils.") || fnName.startsWith("math.") || fnName.startsWith("string.");
    }

    private boolean isStandardKeyword(String root) {
        return "true".equals(root) || "false".equals(root) || "null".equals(root) ||
               "plan".equals(root) || "trigger".equals(root) || "task".equals(root) ||
               "obj".equals(root) || "step".equals(root) || "event".equals(root) ||
               "user".equals(root) || "context".equals(root);
    }
}
