package com.luna.aggarly.aiagent.tool.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.aiagent.tool.booking.record.BookingConfirmationParams;
import com.luna.aggarly.aiagent.tool.booking.record.BookingConfirmationResponse;
import com.luna.aggarly.booking.service.BookingService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookingConfirmationTool implements Tool<BookingConfirmationParams, BookingConfirmationResponse> {

    private final BookingService bookingService;
    private final JsonSchemaService jsonSchemaService;

    @Override
    public String name() {
        return "booking.confirm";
    }

    @Override
    public String description() {
        return "Confirm/approve a pending booking reservation.";
    }

    @Override
    public Class<BookingConfirmationParams> parameterType() {
        return BookingConfirmationParams.class;
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
    public ToolResult<BookingConfirmationResponse> execute(BookingConfirmationParams params, UserPrincipal user) {
        bookingService.confirmBooking(params.bookingId());
        BookingConfirmationResponse resp = new BookingConfirmationResponse("Booking " + params.bookingId() + " successfully confirmed.", true);
        return ToolResult.ok(resp);
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(BookingConfirmationParams.class);
    }

    @Override
    public JsonNode responseSchema() {
        return jsonSchemaService.generate(BookingConfirmationResponse.class);
    }
}

