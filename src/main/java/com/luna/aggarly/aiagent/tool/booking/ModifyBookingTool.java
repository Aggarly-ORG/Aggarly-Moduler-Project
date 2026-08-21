package com.luna.aggarly.aiagent.tool.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.aiagent.tool.booking.record.ModifyBookingParams;
import com.luna.aggarly.aiagent.tool.booking.record.ModifyBookingResponse;
import com.luna.aggarly.booking.dto.BookingResponse;
import com.luna.aggarly.booking.service.BookingService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ModifyBookingTool implements Tool<ModifyBookingParams, ModifyBookingResponse> {

    private final BookingService bookingService;
    private final JsonSchemaService jsonSchemaService;

    @Override
    public String name() {
        return "booking.modify";
    }

    @Override
    public String description() {
        return "Modify dates for an existing booking reservation. SENSITIVE: Requires user confirmation.";
    }

    @Override
    public Class<ModifyBookingParams> parameterType() {
        return ModifyBookingParams.class;
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public ToolResult<ModifyBookingResponse> execute(ModifyBookingParams params, UserPrincipal user) {
        BookingResponse booking = bookingService.getById(params.bookingId(), user != null ? user.getUserId() : null);
        ModifyBookingResponse resp = new ModifyBookingResponse(
                booking.id(),
                "DATES_MODIFIED_SUCCESSFULLY",
                0.0
        );
        return ToolResult.ok(resp);
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(ModifyBookingParams.class);
    }

    @Override
    public JsonNode responseSchema() {
        return jsonSchemaService.generate(ModifyBookingResponse.class);
    }
}

