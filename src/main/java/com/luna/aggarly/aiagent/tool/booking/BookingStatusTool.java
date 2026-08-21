package com.luna.aggarly.aiagent.tool.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.aiagent.tool.booking.record.BookingStatusParams;
import com.luna.aggarly.aiagent.tool.booking.record.BookingStatusResponse;
import com.luna.aggarly.booking.dto.BookingResponse;
import com.luna.aggarly.booking.service.BookingService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookingStatusTool implements Tool<BookingStatusParams, BookingStatusResponse> {

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
    public Class<BookingStatusParams> parameterType() {
        return BookingStatusParams.class;
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
    public ToolResult<BookingStatusResponse> execute(BookingStatusParams params, UserPrincipal user) {
        BookingResponse booking = bookingService.getById(params.bookingId(), user != null ? user.getUserId() : null);
        BookingStatusResponse resp = new BookingStatusResponse(
                booking.id(),
                booking.status() != null ? booking.status() : "UNKNOWN",
                booking.checkIn() != null ? booking.checkIn().toString() : null,
                booking.checkOut() != null ? booking.checkOut().toString() : null
        );
        return ToolResult.ok(resp);
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(BookingStatusParams.class);
    }

    @Override
    public JsonNode responseSchema() {
        return jsonSchemaService.generate(BookingStatusResponse.class);
    }
}

