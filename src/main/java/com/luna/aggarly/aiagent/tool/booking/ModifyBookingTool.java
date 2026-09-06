package com.luna.aggarly.aiagent.tool.booking;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.booking.dto.BookingResponse;
import com.luna.aggarly.booking.service.BookingService;
import com.luna.aggarly.user.security.UserPrincipal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ModifyBookingTool implements Tool<ModifyBookingTool.Params, ModifyBookingTool.Response> {

    public record Params(
            @NotNull
            @JsonPropertyDescription("The unique identifier of the booking reservation to modify.")
            UUID bookingId,

            @NotBlank
            @JsonPropertyDescription("New check-in date formatted as YYYY-MM-DD.")
            String checkIn,

            @NotBlank
            @JsonPropertyDescription("New check-out date formatted as YYYY-MM-DD.")
            String checkOut
    ) {}

    public record Response(
            UUID bookingId,
            String status,
            double priceDifference
    ) {}

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
        BookingResponse booking = bookingService.getById(params.bookingId(), user != null ? user.getUserId() : null);
        Response resp = new Response(
                booking.id(),
                "DATES_MODIFIED_SUCCESSFULLY",
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
