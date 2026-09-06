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
public class BookingStatusTool implements Tool<BookingStatusTool.Params, BookingStatusTool.Response> {

    public record Params(
            @NotNull
            @JsonPropertyDescription("The unique identifier of the booking reservation.")
            UUID bookingId
    ) {}

    public record Response(
            UUID bookingId,
            String status,
            String checkIn,
            String checkOut
    ) {}

    private final BookingService bookingService;
    private final JsonSchemaService jsonSchemaService;

    @Override
    public String name() {
        return "booking.status";
    }

    @Override
    public String description() {
        return "Retrieve the status and details of a booking reservation by booking ID.";
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
                booking.status() != null ? booking.status() : "UNKNOWN",
                booking.checkIn() != null ? booking.checkIn().toString() : null,
                booking.checkOut() != null ? booking.checkOut().toString() : null
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
