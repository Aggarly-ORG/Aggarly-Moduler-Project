package com.luna.aggarly.common.expression;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.common.expression.spi.ExpressionContext;
import com.luna.aggarly.common.expression.spi.ExpressionFunction;
import com.luna.aggarly.common.expression.spi.NamespaceResolver;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ExpressionEngine {

    private static final Pattern SINGLE_EXPR_PATTERN = Pattern.compile("^(?:\\$?\\{\\{\\s*(.+?)\\s*\\}\\}|\\$\\{\\s*(.+?)\\s*\\})$");
    private static final Pattern EMBEDDED_EXPR_PATTERN = Pattern.compile("(?:\\$?\\{\\{\\s*(.+?)\\s*\\}\\}|\\$\\{\\s*(.+?)\\s*\\})");

    private final Map<String, NamespaceResolver> namespaceResolvers = new HashMap<>();
    private final Map<String, ExpressionFunction> functions = new HashMap<>();
    private final ObjectMapper objectMapper;

    @Getter
    @Setter
    private EvaluationMode defaultMode = EvaluationMode.LENIENT;

    @Autowired
    public ExpressionEngine(
            @Autowired(required = false) List<NamespaceResolver> resolvers,
            @Autowired(required = false) List<ExpressionFunction> functionList,
            @Autowired(required = false) org.springframework.context.ApplicationContext applicationContext,
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        if (resolvers != null) {
            for (NamespaceResolver r : resolvers) {
                this.namespaceResolvers.put(r.namespace().toLowerCase(), r);
            }
        }
        if (functionList != null) {
            for (ExpressionFunction f : functionList) {
                this.functions.put(f.name().toLowerCase(), f);
            }
        }
        if (applicationContext != null) {
            scanAndRegisterMethodFunctions(applicationContext);
        }
        log.info("ExpressionEngine initialized with {} namespaces ({}) and {} functions ({})",
                namespaceResolvers.size(), namespaceResolvers.keySet(),
                functions.size(), functions.keySet());
    }

    private void scanAndRegisterMethodFunctions(org.springframework.context.ApplicationContext ctx) {
        try {
            Map<String, Object> libBeans = ctx.getBeansWithAnnotation(com.luna.aggarly.common.expression.annotation.ExpressionFunctionLibrary.class);
            for (Object bean : libBeans.values()) {
                registerLibrary(bean);
            }
        } catch (Exception ex) {
            log.warn("Error scanning for expression function libraries: {}", ex.getMessage());
        }
    }

    public void registerLibrary(Object bean) {
        if (bean == null) return;
        Class<?> clazz = bean.getClass();
        String classPrefix = "";
        if (clazz.isAnnotationPresent(com.luna.aggarly.common.expression.annotation.ExpressionFunctionLibrary.class)) {
            com.luna.aggarly.common.expression.annotation.ExpressionFunctionLibrary libAnn =
                    clazz.getAnnotation(com.luna.aggarly.common.expression.annotation.ExpressionFunctionLibrary.class);
            if (libAnn != null && !libAnn.prefix().isBlank()) {
                classPrefix = libAnn.prefix() + ".";
            }
        }

        for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(com.luna.aggarly.common.expression.annotation.ExpressionFunction.class)) {
                var ann = method.getAnnotation(com.luna.aggarly.common.expression.annotation.ExpressionFunction.class);
                String fnName = !ann.name().isBlank() ? ann.name() : (!ann.value().isBlank() ? ann.value() : method.getName());

                ReflectiveExpressionFunction refFn = new ReflectiveExpressionFunction(fnName, bean, method, objectMapper);
                registerFunction(refFn);

                if (!classPrefix.isBlank()) {
                    registerFunction(new ReflectiveExpressionFunction(classPrefix + fnName, bean, method, objectMapper));
                }

                for (String alias : ann.aliases()) {
                    if (!alias.isBlank()) {
                        registerFunction(new ReflectiveExpressionFunction(alias, bean, method, objectMapper));
                    }
                }
            }
        }
    }

    public void registerNamespace(NamespaceResolver resolver) {
        if (resolver != null) {
            this.namespaceResolvers.put(resolver.namespace().toLowerCase(), resolver);
        }
    }

    public void registerFunction(ExpressionFunction function) {
        if (function != null) {
            this.functions.put(function.name().toLowerCase(), function);
        }
    }

    public Set<String> getAvailableNamespaces() {
        return Collections.unmodifiableSet(namespaceResolvers.keySet());
    }

    public Set<String> getAvailableFunctions() {
        return Collections.unmodifiableSet(functions.keySet());
    }

    /**
     * Resolves expressions within any object (Map, List, String, or primitive).
     */
    public Object resolve(Object value, ExpressionContext context) {
        return resolve(value, context, defaultMode);
    }

    public Object resolve(Object value, ExpressionContext context, EvaluationMode mode) {
        if (value == null) {
            return null;
        }

        if (value instanceof String str) {
            return resolveString(str, context, mode);
        }

        if (value instanceof Map<?, ?> map) {
            Map<String, Object> resolvedMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                resolvedMap.put(key, resolve(entry.getValue(), context, mode));
            }
            return resolvedMap;
        }

        if (value instanceof List<?> list) {
            List<Object> resolvedList = new ArrayList<>();
            for (Object item : list) {
                resolvedList.add(resolve(item, context, mode));
            }
            return resolvedList;
        }

        return value;
    }

    public Object resolveString(String input, ExpressionContext context, EvaluationMode mode) {
        if (input == null || input.isBlank()) {
            return input;
        }

        String trimmed = input.trim();
        if ((trimmed.startsWith("{{") && trimmed.endsWith("}}")) || (trimmed.startsWith("${") && trimmed.endsWith("}"))) {
            return evaluateExpression(trimmed, context, mode);
        }

        Matcher embeddedMatcher = EMBEDDED_EXPR_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (embeddedMatcher.find()) {
            String expr = embeddedMatcher.group(1) != null ? embeddedMatcher.group(1) : embeddedMatcher.group(2);
            Object evaluated = evaluateExpression(expr.trim(), context, mode);
            String replacement = evaluated != null ? evaluated.toString() : "";
            embeddedMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        embeddedMatcher.appendTail(sb);
        return sb.toString();
    }

    public Object evaluateExpression(String expr, ExpressionContext context, EvaluationMode mode) {
        if (expr == null || expr.isBlank()) {
            return null;
        }

        try {
            expr = expr.trim();

            // Strip any redundant nested enclosing braces {{ ... }} or ${ ... }
            while ((expr.startsWith("{{") && expr.endsWith("}}")) || (expr.startsWith("${") && expr.endsWith("}"))) {
                if (expr.startsWith("{{") && expr.endsWith("}}") && expr.length() >= 4) {
                    expr = expr.substring(2, expr.length() - 2).trim();
                } else if (expr.startsWith("${") && expr.endsWith("}") && expr.length() >= 3) {
                    expr = expr.substring(2, expr.length() - 1).trim();
                } else {
                    break;
                }
            }

            // Literal string
            if (Tokenizer.isQuoted(expr)) {
                return Tokenizer.unquote(expr);
            }

            // Literal boolean
            if ("true".equalsIgnoreCase(expr)) return Boolean.TRUE;
            if ("false".equalsIgnoreCase(expr)) return Boolean.FALSE;
            if ("null".equalsIgnoreCase(expr)) return null;

            // Literal number
            try {
                if (expr.matches("^-?\\d+$")) {
                    return Long.parseLong(expr);
                }
                if (expr.matches("^-?\\d*\\.\\d+$")) {
                    return Double.parseDouble(expr);
                }
            } catch (NumberFormatException ignored) {}

            // Function invocation check: fnName(arg1, arg2...)
            int firstParen = expr.indexOf('(');
            if (firstParen > 0 && expr.endsWith(")")) {
                String fnName = expr.substring(0, firstParen).trim();
                String argsContent = expr.substring(firstParen + 1, expr.length() - 1);
                List<String> rawArgs = Tokenizer.splitArguments(argsContent);
                List<Object> evaluatedArgs = new ArrayList<>();
                for (String rawArg : rawArgs) {
                    evaluatedArgs.add(evaluateExpression(rawArg, context, mode));
                }

                ExpressionFunction fn = functions.get(fnName.toLowerCase());
                if (fn != null) {
                    return fn.execute(evaluatedArgs, context);
                } else if (mode == EvaluationMode.STRICT) {
                    throw new IllegalArgumentException("Unknown expression function: " + fnName);
                } else {
                    log.warn("Unknown expression function '{}'", fnName);
                    return null;
                }
            }

            // Path & Namespace resolution
            return evaluatePath(expr, context, mode);

        } catch (Exception ex) {
            if (mode == EvaluationMode.STRICT) {
                if (ex instanceof RuntimeException re) throw re;
                throw new IllegalStateException("Failed to evaluate expression: " + expr, ex);
            }
            log.warn("Expression evaluation failed for '{}': {}", expr, ex.getMessage());
            return null;
        }
    }

    private Object evaluatePath(String expr, ExpressionContext context, EvaluationMode mode) {
        // Zero-arg function fallback without parentheses (e.g. "today", "now", "uuid")
        if (functions.containsKey(expr.toLowerCase())) {
            ExpressionFunction fn = functions.get(expr.toLowerCase());
            return fn.execute(List.of(), context);
        }

        // Normalize bracket access: "items[0].id" -> "items.0.id"
        String normalizedPath = expr.replaceAll("\\[(\\d+)\\]", ".$1");

        String[] parts = normalizedPath.split("\\.");
        if (parts.length == 0) return null;

        String rootNamespace = parts[0].toLowerCase();
        String remainingPath = normalizedPath.length() > parts[0].length()
                ? normalizedPath.substring(parts[0].length() + 1)
                : "";

        NamespaceResolver resolver = namespaceResolvers.get(rootNamespace);
        if (resolver != null) {
            return resolver.resolve(remainingPath, context);
        }

        // Direct variable or root object lookup fallback
        if (context != null) {
            if (context.variables() != null && context.variables().containsKey(parts[0])) {
                Object val = context.variables().get(parts[0]);
                return extractNestedProperty(val, remainingPath);
            }
            if (context.rootObject() != null && context.rootObject().containsKey(parts[0])) {
                Object val = context.rootObject().get(parts[0]);
                return extractNestedProperty(val, remainingPath);
            }
        }

        if (mode == EvaluationMode.STRICT) {
            throw new IllegalArgumentException("Unknown expression root or namespace: " + parts[0]);
        }
        return null;
    }

    public Object extractNestedProperty(Object root, String propertyPath) {
        return NestedPropertyExtractor.extract(root, propertyPath, objectMapper);
    }
}
