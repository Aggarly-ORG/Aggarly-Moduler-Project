package com.luna.aggarly.aiagent.tool.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.aiagent.tool.booking.record.CheckInInstructionsParams;
import com.luna.aggarly.aiagent.tool.booking.record.CheckInInstructionsResponse;
import com.luna.aggarly.booking.dto.BookingResponse;
import com.luna.aggarly.booking.service.BookingService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CheckInInstructionsTool implements Tool<CheckInInstructionsParams, CheckInInstructionsResponse> {

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
    public Class<CheckInInstructionsParams> parameterType() {
        return CheckInInstructionsParams.class;
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
    public ToolResult<CheckInInstructionsResponse> execute(CheckInInstructionsParams params, UserPrincipal user) {
        BookingResponse booking = bookingService.getById(params.bookingId(), user != null ? user.getUserId() : null);
        CheckInInstructionsResponse resp = new CheckInInstructionsResponse(
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
        return jsonSchemaService.generate(CheckInInstructionsParams.class);
    }

    @Override
    public JsonNode responseSchema() {
        return jsonSchemaService.generate(CheckInInstructionsResponse.class);
    }
}

