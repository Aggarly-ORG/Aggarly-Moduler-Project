package com.luna.aggarly.aiagent.tool.property;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.availability.dto.AvailabilityCalendarResponse;
import com.luna.aggarly.availability.dto.AvailabilityCheckResponse;
import com.luna.aggarly.availability.service.AvailabilityService;
import com.luna.aggarly.user.security.UserPrincipal;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PropertyCalendarTool implements Tool<PropertyCalendarTool.Params, PropertyCalendarTool.Response> {

    public record Params(
            @NotNull(message = "Property ID is required.")
            @JsonPropertyDescription("The UUID of the property whose availability to inspect.")
            UUID propertyId,

            @JsonPropertyDescription("Optional check-in date (YYYY-MM-DD). When provided together with 'checkOut', " +
                    "returns an availability verdict for that exact range instead of the full calendar.")
            LocalDate checkIn,

            @JsonPropertyDescription("Optional check-out date (YYYY-MM-DD). Required whenever 'checkIn' is provided.")
            LocalDate checkOut,

            @JsonPropertyDescription("Optional start date for the calendar view (defaults to today).")
            LocalDate from,

            @JsonPropertyDescription("Optional end date for the calendar view (defaults to one month from today).")
            LocalDate to
    ) {}

    public record Response(
            UUID propertyId,
            boolean isAvailable,
            String checkIn,
            String checkOut,
            List<String> bookedDates,
            List<String> blockedDates,
            List<SlotItem> slots,
            String summary
    ) {
        public record SlotItem(
                String date,
                String status,
                Double pricePerNight
        ) {}

        public static Response forRangeCheck(UUID propertyId, boolean available, String checkIn, String checkOut, String reason) {
            String summary = available
                    ? String.format("Property is AVAILABLE from %s to %s.", checkIn, checkOut)
                    : String.format("Property is NOT available from %s to %s (%s).", checkIn, checkOut,
                    reason != null ? reason : "conflicting reservation");
            return new Response(propertyId, available, checkIn, checkOut, List.of(), List.of(), List.of(), summary);
        }

        public static Response forCalendar(UUID propertyId, List<String> bookedDates, List<String> blockedDates,
                                           List<SlotItem> slots, String from, String to) {
            String summary = String.format("Calendar loaded from %s to %s: %d booked dates, %d blocked dates.",
                    from, to, bookedDates.size(), blockedDates.size());
            return new Response(propertyId, true, null, null, bookedDates, blockedDates, slots, summary);
        }
    }

    private final AvailabilityService availabilityService;
    private final JsonSchemaService jsonSchemaService;

    @Override
    public String name() {
        return "property.calendar";
    }

    @Override
    public String description() {
        return "Inspect a property's availability. With checkIn+checkOut dates: verifies whether that exact range is " +
                "bookable and returns a verdict. Without them: returns the full booked/blocked calendar (default one month). " +
                "Typically used after searching properties while the guest decides on dates.";
    }

    @Override
    public Class<Params> parameterType() {
        return Params.class;
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
    public ToolResult<Response> execute(Params params, UserPrincipal user) {
        log.info("Executing property.calendar: propertyId={}, checkIn={}, checkOut={}, from={}, to={}",
                params.propertyId(), params.checkIn(), params.checkOut(), params.from(), params.to());

        if (params.propertyId() == null) {
            return ToolResult.failed("INVALID_PARAMS", "propertyId is required.");
        }

        if (params.checkIn() != null && params.checkOut() != null) {
            return executeRangeCheck(params);
        }

        if (params.checkIn() != null && params.checkOut() == null) {
            return ToolResult.failed("MISSING_CHECKOUT",
                    "When providing checkIn, checkOut must also be specified.");
        }

        return executeFullCalendar(params);
    }

    private ToolResult<Response> executeRangeCheck(Params params) {
        LocalDate checkIn = params.checkIn();
        LocalDate checkOut = params.checkOut();

        if (!checkOut.isAfter(checkIn)) {
            return ToolResult.failed("INVALID_DATES", "checkOut date must be after checkIn date.");
        }

        try {
            AvailabilityCheckResponse check = availabilityService.checkAvailability(
                    params.propertyId(), checkIn, checkOut);

            Response resp = Response.forRangeCheck(
                    params.propertyId(),
                    check.available(),
                    checkIn.toString(),
                    checkOut.toString(),
                    check.reason()
            );
            return ToolResult.ok(resp);
        } catch (Exception ex) {
            log.error("Failed to check availability for property {}: {}", params.propertyId(), ex.getMessage(), ex);
            return ToolResult.failed("AVAILABILITY_CHECK_FAILED",
                    "Could not verify availability: " + ex.getMessage());
        }
    }

    private ToolResult<Response> executeFullCalendar(Params params) {
        LocalDate from = params.from() != null ? params.from() : LocalDate.now();
        LocalDate to = params.to() != null ? params.to() : from.plusMonths(1);

        if (!to.isAfter(from)) {
            return ToolResult.failed("INVALID_DATES", "'to' date must be after 'from' date.");
        }

        try {
            AvailabilityCalendarResponse cal = availabilityService.getCalendar(
                    params.propertyId(), from, to);

            List<String> blockedDates = cal.slots() != null
                    ? cal.slots().stream()
                    .filter(s -> !s.available())
                    .map(s -> s.startDate() != null ? s.startDate().toString() : "")
                    .filter(s -> !s.isEmpty())
                    .toList()
                    : List.of();

            List<Response.SlotItem> slotItems = cal.slots() != null
                    ? cal.slots().stream()
                    .map(s -> new Response.SlotItem(
                            s.startDate() != null ? s.startDate().toString() : null,
                            s.available() ? "AVAILABLE" : (s.blockReason() != null ? s.blockReason() : "BLOCKED"),
                            null))
                    .toList()
                    : List.of();

            Response resp = Response.forCalendar(
                    params.propertyId(),
                    List.of(),
                    blockedDates,
                    slotItems,
                    from.toString(),
                    to.toString()
            );
            return ToolResult.ok(resp);
        } catch (Exception ex) {
            log.error("Failed to load calendar for property {}: {}", params.propertyId(), ex.getMessage(), ex);
            return ToolResult.failed("CALENDAR_LOAD_FAILED",
                    "Could not load property calendar: " + ex.getMessage());
        }
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(Params.class);
    }

    @Override
    public JsonNode responseSchema() {
        return jsonSchemaService.generate(Response.class);
    }
}
