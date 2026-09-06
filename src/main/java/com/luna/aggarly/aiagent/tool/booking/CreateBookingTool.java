package com.luna.aggarly.aiagent.tool.booking;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.booking.dto.BookingResponse;
import com.luna.aggarly.booking.dto.CreateBookingRequest;
import com.luna.aggarly.booking.service.BookingService;
import com.luna.aggarly.user.security.UserPrincipal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CreateBookingTool implements Tool<CreateBookingTool.Params, CreateBookingTool.Response> {

    public record Params(
            @NotNull
            @JsonPropertyDescription("The unique identifier of the target property to book.")
            UUID propertyId,

            @NotBlank
            @JsonPropertyDescription("Check-in date formatted as YYYY-MM-DD.")
            String checkIn,

            @NotBlank
            @JsonPropertyDescription("Check-out date formatted as YYYY-MM-DD.")
            String checkOut,

            @JsonPropertyDescription("Number of guests staying at the property.")
            int guests
    ) {}

    public record Response(
            UUID bookingId,
            String status,
            double totalPrice,
            String propertyTitle,
            String message
    ) {}

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
        CreateBookingRequest req = new CreateBookingRequest(
                params.propertyId(),
                LocalDate.parse(params.checkIn()),
                LocalDate.parse(params.checkOut()),
                params.guests() > 0 ? params.guests() : 1,
                null
        );
        BookingResponse bookingResp = bookingService.createBooking(req, user != null ? user.getUserId() : null);
        double total = bookingResp.totalAmount() != null ? bookingResp.totalAmount().doubleValue() : 0.0;
        Response resp = new Response(
                bookingResp.id(),
                bookingResp.status() != null ? bookingResp.status() : "PENDING_PAYMENT",
                total,
                "Aggarly Property",
                "Booking successfully created and awaiting payment/confirmation."
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
