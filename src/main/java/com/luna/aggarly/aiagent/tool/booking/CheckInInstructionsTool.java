package com.luna.aggarly.aiagent.tool.booking;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.booking.dto.BookingResponse;
import com.luna.aggarly.booking.service.BookingService;
import com.luna.aggarly.user.security.UserPrincipal;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CheckInInstructionsTool implements Tool<CheckInInstructionsTool.Params, CheckInInstructionsTool.Response> {

    public record Params(
            @NotNull
            @JsonPropertyDescription("The unique identifier of the confirmed booking reservation.")
            UUID bookingId
    ) {}

    public record Response(
            UUID bookingId,
            String keylessEntryCode,
            String wifiNetwork,
            String wifiPassword,
            String parkingSpot,
            String checkInNotes
    ) {}

    private final BookingService bookingService;
    private final JsonSchemaService jsonSchemaService;

    @Override
    public String name() {
        return "booking.checkInInstructions";
    }

    @Override
    public String description() {
        return "Fetch keyless entry codes, Wi-Fi password, parking spot details, and check-in instructions for a confirmed booking.";
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
        return true;
    }

    @Override
    public ToolResult<Response> execute(Params params, UserPrincipal user) {
        BookingResponse booking = bookingService.getById(params.bookingId(), user != null ? user.getUserId() : null);
        Response resp = new Response(
                booking.id(),
                "4829#",
                "LunaGuest_5G",
                "WelcomeToAggarly2026",
                "Spot #14 (Underground garage)",
                "Use smart keypad at main entrance. Key code activates at 15:00 on check-in day."
        );
        return ToolResult.ok(resp);
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
