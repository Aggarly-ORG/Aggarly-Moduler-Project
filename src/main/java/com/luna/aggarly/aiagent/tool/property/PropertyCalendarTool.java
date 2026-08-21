package com.luna.aggarly.aiagent.tool.property;

import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.aiagent.tool.property.record.PropertyCalendarParams;
import com.luna.aggarly.aiagent.tool.property.record.PropertyCalendarToolResponse;
import com.luna.aggarly.availability.dto.AvailabilityCalendarResponse;
import com.luna.aggarly.availability.dto.AvailabilitySlotResponse;
import com.luna.aggarly.availability.service.AvailabilityService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PropertyCalendarTool implements Tool<PropertyCalendarParams, PropertyCalendarToolResponse> {

    private final AvailabilityService availabilityService;
    private final JsonSchemaService jsonSchemaService;

    @Override
    public String name() {
        return "property.calendar";
    }

    @Override
    public String description() {
        return "Retrieve the full booking availability calendar and booked/blocked dates for a specific property directly using only its propertyId. it's used while the user still ask about the property and want to know the calender to book so most time used after searching about properties.";
    }

    @Override
    public Class<PropertyCalendarParams> parameterType() {
        return PropertyCalendarParams.class;
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
    public ToolResult<PropertyCalendarToolResponse> execute(PropertyCalendarParams params, UserPrincipal user) {
        LocalDate fromDate = params.from() != null ? params.from() : LocalDate.now();
        LocalDate toDate = params.to() != null ? params.to() : fromDate.plusMonths(1);

        log.info("Executing property.calendar for propertyId={}, range={} to {}",
                params.propertyId(), fromDate, toDate);

        try {
            AvailabilityCalendarResponse calendar = availabilityService.getCalendar(
                    params.propertyId(), fromDate, toDate
            );

            List<AvailabilitySlotResponse> slots = (calendar != null && calendar.slots() != null)
                    ? calendar.slots()
                    : List.of();

            int blockedCount = (int) slots.stream().filter(s -> !s.available()).count();
            String summary = String.format("Calendar retrieved from %s to %s with %d booked/blocked periods.",
                    fromDate, toDate, blockedCount);

            PropertyCalendarToolResponse response = new PropertyCalendarToolResponse(
                    params.propertyId(),
                    fromDate,
                    toDate,
                    slots,
                    blockedCount,
                    summary
            );

            return ToolResult.ok(response);
        } catch (Exception ex) {
            log.error("Failed to retrieve property calendar for propertyId={}", params.propertyId(), ex);
            return ToolResult.failed("CALENDAR_FETCH_FAILED", "Could not fetch property calendar: " + ex.getMessage());
        }
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(PropertyCalendarParams.class);
    }

    @Override
    public JsonNode responseSchema() {
        return jsonSchemaService.generate(PropertyCalendarToolResponse.class);
    }
}
