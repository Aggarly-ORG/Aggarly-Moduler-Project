package com.luna.aggarly.scheduler.operation.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.luna.aggarly.booking.dto.BookingResponse;
import com.luna.aggarly.booking.service.BookingService;
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
public class BookingGetOperation implements WorkflowOperation {

    private final BookingService bookingService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "booking.getById";
    }

    @Override
    public String description() {
        return "Fetches detailed reservation information for a given booking UUID.";
    }

    @Override
    public JsonNode parameterSchema() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        ObjectNode props = root.putObject("properties");
        props.putObject("bookingId").put("type", "string");
        return root;
    }

    @Override
    public JsonNode responseSchema() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        ObjectNode props = root.putObject("properties");
        props.putObject("id").put("type", "string");
        props.putObject("propertyId").put("type", "string");
        props.putObject("status").put("type", "string");
        props.putObject("checkIn").put("type", "string");
        props.putObject("checkOut").put("type", "string");
        props.putObject("totalAmount").put("type", "number");
        return root;
    }

    @Override
    public Object execute(Object arguments, ExecutionContext context) {
        UUID bookingId = null;
        if (arguments instanceof Map<?, ?> map && map.containsKey("bookingId")) {
            bookingId = UUID.fromString(String.valueOf(map.get("bookingId")));
        } else if (arguments instanceof String str) {
            bookingId = UUID.fromString(str);
        }

        if (bookingId == null) {
            throw new IllegalArgumentException("bookingId parameter is required for booking.getById");
        }

        log.info("Executing booking.getById for bookingId={}", bookingId);
        BookingResponse booking = bookingService.getById(bookingId, context.userId());
        return objectMapper.convertValue(booking, Map.class);
    }
}
