package com.luna.aggarly.aiagent.tool.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.aiagent.tool.booking.record.CancelBookingParams;
import com.luna.aggarly.aiagent.tool.booking.record.CancelBookingResponse;
import com.luna.aggarly.booking.dto.BookingResponse;
import com.luna.aggarly.booking.dto.CancelBookingRequest;
import com.luna.aggarly.booking.service.BookingService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CancelBookingTool implements Tool<CancelBookingParams, CancelBookingResponse> {

    private final BookingService bookingService;
    private final JsonSchemaService jsonSchemaService;

    @Override
    public String name() {
        return "booking.cancel";
    }

    @Override
    public String description() {
        return "Cancel a booking reservation and trigger refund calculation. SENSITIVE: Requires user confirmation.";
    }

    @Override
    public Class<CancelBookingParams> parameterType() {
        return CancelBookingParams.class;
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
    public ToolResult<CancelBookingResponse> execute(CancelBookingParams params, UserPrincipal user) {
        CancelBookingRequest req = new CancelBookingRequest(params.reason() != null ? params.reason() : "Cancelled by guest via AI Assistant");
        BookingResponse cancelResp = bookingService.cancelBooking(
                params.bookingId(),
                req,
                user != null ? user.getUserId() : null
        );
        CancelBookingResponse resp = new CancelBookingResponse(
                params.bookingId(),
                "Booking cancelled successfully.",
                0.0
        );
        return ToolResult.ok(resp);
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(CancelBookingParams.class);
    }

    @Override
    public JsonNode responseSchema() {
        return jsonSchemaService.generate(CancelBookingResponse.class);
    }
}

