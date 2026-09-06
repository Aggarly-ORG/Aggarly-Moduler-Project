package com.luna.aggarly.common.expression;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NestedPropertyExtractor {

    private static final ObjectMapper DEFAULT_MAPPER = new ObjectMapper();

    public static Object extract(Object root, String propertyPath) {
        return extract(root, propertyPath, DEFAULT_MAPPER);
    }

    public static Object extract(Object root, String propertyPath, ObjectMapper mapper) {
        if (root == null || propertyPath == null || propertyPath.isBlank()) {
            return root;
        }

        ObjectMapper objectMapper = mapper != null ? mapper : DEFAULT_MAPPER;

        try {
            String normalized = propertyPath.replaceAll("\\[(\\d+)\\]", ".$1");
            String[] segments = normalized.split("\\.");

            JsonNode current = (root instanceof JsonNode jn) ? jn : objectMapper.valueToTree(root);

            for (String segment : segments) {
                if (current == null || current.isMissingNode() || current.isNull()) {
                    return null;
                }

                if (current.isArray()) {
                    try {
                        int idx = Integer.parseInt(segment);
                        current = (idx >= 0 && idx < current.size()) ? current.get(idx) : null;
                        continue;
                    } catch (NumberFormatException ignored) {}
                }

                current = current.get(segment);
            }

            if (current == null || current.isMissingNode() || current.isNull()) {
                return null;
            }

            if (current.isTextual()) return current.asText();
            if (current.isNumber()) return current.numberValue();
            if (current.isBoolean()) return current.asBoolean();
            return objectMapper.treeToValue(current, Object.class);

        } catch (Exception ex) {
            log.debug("Could not extract property '{}' from target: {}", propertyPath, ex.getMessage());
            return null;
        }
    }
}
