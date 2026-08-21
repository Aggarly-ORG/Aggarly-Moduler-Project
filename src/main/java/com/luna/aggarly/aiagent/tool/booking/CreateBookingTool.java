package com.luna.aggarly.aiagent.tool.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.aiagent.tool.booking.record.CreateBookingParams;
import com.luna.aggarly.aiagent.tool.booking.record.CreateBookingResponse;
import com.luna.aggarly.booking.dto.BookingResponse;
import com.luna.aggarly.booking.dto.CreateBookingRequest;
import com.luna.aggarly.booking.service.BookingService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class CreateBookingTool implements Tool<CreateBookingParams, CreateBookingResponse> {

    private final BookingService bookingService;
    private final JsonSchemaService jsonSchemaService;

    @Override
    public String name() {
        return "booking.create";
    }

    @Override
    public String description() {
        return "Create a new booking reservation for a property. SENSITIVE: Requires user confirmation.";
    }

    @Override
    public Class<CreateBookingParams> parameterType() {
        return CreateBookingParams.class;
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
    public ToolResult<CreateBookingResponse> execute(CreateBookingParams params, UserPrincipal user) {
        CreateBookingRequest req = new CreateBookingRequest(
                params.propertyId(),
                LocalDate.parse(params.checkIn()),
                LocalDate.parse(params.checkOut()),
                params.guests() > 0 ? params.guests() : 1,
                null
        );
        BookingResponse bookingResp = bookingService.createBooking(req, user != null ? user.getUserId() : null);
        double total = bookingResp.totalAmount() != null ? bookingResp.totalAmount().doubleValue() : 0.0;
        CreateBookingResponse resp = new CreateBookingResponse(
                bookingResp.id(),
                bookingResp.status() != null ? bookingResp.status() : "PENDING_PAYMENT",
                total,
                total,
                "€",
                bookingResp.clientSecret()
        );
        return ToolResult.ok(resp);
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(CreateBookingParams.class);
    }

    @Override
    public JsonNode responseSchema() {
        return jsonSchemaService.generate(CreateBookingResponse.class);
    }
}

