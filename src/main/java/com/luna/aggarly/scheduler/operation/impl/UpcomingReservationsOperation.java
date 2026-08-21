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

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpcomingReservationsOperation implements WorkflowOperation {

    private final BookingService bookingService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "host.upcomingReservations";
    }

    @Override
    public String description() {
        return "Fetches upcoming guest reservations and check-in dates for a host.";
    }

    @Override
    public JsonNode parameterSchema() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        return root;
    }

    @Override
    public JsonNode responseSchema() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        ObjectNode props = root.putObject("properties");
        props.putObject("reservations").put("type", "array");
        props.putObject("totalCount").put("type", "integer");
        return root;
    }

    @Override
    public Object execute(Object arguments, ExecutionContext context) {
        log.info("Executing host.upcomingReservations for user {}", context.userId());
        List<BookingResponse> bookings = bookingService.getMyBookings(context.userId());
        return Map.of(
                "reservations", bookings != null ? bookings : List.of(),
                "totalCount", bookings != null ? bookings.size() : 0
        );
    }
}
