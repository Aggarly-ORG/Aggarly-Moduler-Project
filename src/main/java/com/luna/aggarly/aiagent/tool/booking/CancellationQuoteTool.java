package com.luna.aggarly.aiagent.tool.booking;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.booking.dto.CancellationQuoteResponse;
import com.luna.aggarly.booking.service.BookingService;
import com.luna.aggarly.user.security.UserPrincipal;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CancellationQuoteTool implements Tool<CancellationQuoteTool.Params, CancellationQuoteTool.Response> {

    public record Params(
            @NotNull
            @JsonPropertyDescription("The unique identifier of the booking reservation to evaluate.")
            UUID bookingId
    ) {}

    public record Response(
            UUID bookingId,
            double refundAmount,
            String policyExplanation
    ) {}

    private final BookingService bookingService;
    private final JsonSchemaService jsonSchemaService;

    @Override
    public String name() {
        return "booking.cancellationQuote";
    }

    @Override
    public String description() {
        return "Calculate the exact refund amount and penalty fees if a booking were to be cancelled now.";
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
        CancellationQuoteResponse quote = bookingService.getCancellationQuote(
                params.bookingId(),
                user != null ? user.getUserId() : null
        );
        Response resp = new Response(
                params.bookingId(),
                quote.refundAmount() != null ? quote.refundAmount().doubleValue() : 0.0,
                quote.policyExplanation() != null ? quote.policyExplanation() : "FLEXIBLE"
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
