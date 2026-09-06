package com.luna.aggarly.aiagent.tool.booking;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.booking.dto.BookingResponse;
import com.luna.aggarly.booking.dto.CancelBookingRequest;
import com.luna.aggarly.booking.service.BookingService;
import com.luna.aggarly.user.security.UserPrincipal;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CancelBookingTool implements Tool<CancelBookingTool.Params, CancelBookingTool.Response> {

    public record Params(
            @NotNull
            @JsonPropertyDescription("The unique identifier of the booking reservation to cancel.")
            UUID bookingId,

            @JsonPropertyDescription("Optional reason for the cancellation.")
            String reason
    ) {}

    public record Response(
            UUID bookingId,
            String message,
            double refundAmount
    ) {}

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
    public Class<Params> parameterType() {
        return Params.class;
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
    public ToolResult<Response> execute(Params params, UserPrincipal user) {
        CancelBookingRequest req = new CancelBookingRequest(params.reason() != null ? params.reason() : "Cancelled by guest via AI Assistant");
        BookingResponse cancelResp = bookingService.cancelBooking(
                params.bookingId(),
                req,
                user != null ? user.getUserId() : null
        );
        Response resp = new Response(
                params.bookingId(),
                "Booking cancelled successfully.",
                0.0
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
