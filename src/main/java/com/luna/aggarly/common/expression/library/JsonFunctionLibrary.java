package com.luna.aggarly.common.expression.library;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.common.expression.NestedPropertyExtractor;
import com.luna.aggarly.common.expression.annotation.ExpressionFunction;
import com.luna.aggarly.common.expression.annotation.ExpressionFunctionLibrary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ExpressionFunctionLibrary(value = "json", prefix = "JsonUtils")
public class JsonFunctionLibrary {

    private final ObjectMapper objectMapper;

    @ExpressionFunction(value = "json_get", description = "Extracts nested property from JSON string using dot/bracket path")
    public Object jsonGet(String jsonStr, String path) {
        if (jsonStr == null || jsonStr.isBlank()) return null;
        try {
            JsonNode node = objectMapper.readTree(jsonStr);
            return NestedPropertyExtractor.extract(node, path, objectMapper);
        } catch (Exception ex) {
            log.debug("json_get parse error: {}", ex.getMessage());
            return null;
        }
    }

    @ExpressionFunction(value = "json_stringify", description = "Serializes object to JSON string")
    public String jsonStringify(Object obj) {
        if (obj == null) return "null";
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception ex) {
            return String.valueOf(obj);
        }
    }

    @ExpressionFunction(value = "json_parse", description = "Parses JSON string into Map or List")
    public Object jsonParse(String jsonStr) {
        if (jsonStr == null || jsonStr.isBlank()) return null;
        try {
            return objectMapper.readValue(jsonStr, Object.class);
        } catch (Exception ex) {
            return null;
        }
    }
}
