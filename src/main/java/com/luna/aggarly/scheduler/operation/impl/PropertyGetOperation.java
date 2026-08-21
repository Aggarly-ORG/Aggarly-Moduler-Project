package com.luna.aggarly.scheduler.operation.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.luna.aggarly.property.dto.response.PropertyResponse;
import com.luna.aggarly.property.service.PropertyService;
import com.luna.aggarly.scheduler.operation.WorkflowOperation;
import com.luna.aggarly.scheduler.workflow.ExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PropertyGetOperation implements WorkflowOperation {

    private final PropertyService propertyService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "property.getById";
    }

    @Override
    public String description() {
        return "Retrieves listing information for a given property UUID.";
    }

    @Override
    public JsonNode parameterSchema() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        ObjectNode props = root.putObject("properties");
        props.putObject("propertyId").put("type", "string");
        return root;
    }

    @Override
    public JsonNode responseSchema() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        ObjectNode props = root.putObject("properties");
        props.putObject("id").put("type", "string");
        props.putObject("title").put("type", "string");
        props.putObject("pricePerNight").put("type", "number");
        return root;
    }

    @Override
    public Object execute(Object arguments, ExecutionContext context) {
        UUID propertyId = null;
        if (arguments instanceof Map<?, ?> map && map.containsKey("propertyId")) {
            propertyId = UUID.fromString(String.valueOf(map.get("propertyId")));
        } else if (arguments instanceof String str) {
            propertyId = UUID.fromString(str);
        }

        if (propertyId == null) {
            throw new IllegalArgumentException("propertyId parameter is required for property.getById");
        }

        log.info("Executing property.getById for propertyId={}", propertyId);
        PropertyResponse property = propertyService.getPropertyById(propertyId);
        return objectMapper.convertValue(property, Map.class);
    }
}
