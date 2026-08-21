package com.luna.aggarly.aiagent.tool.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.aiagent.tool.booking.record.CancellationQuoteParams;
import com.luna.aggarly.aiagent.tool.booking.record.CancellationQuoteToolResponse;
import com.luna.aggarly.booking.dto.CancellationQuoteResponse;
import com.luna.aggarly.booking.service.BookingService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CancellationQuoteTool implements Tool<CancellationQuoteParams, CancellationQuoteToolResponse> {

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
    public Class<CancellationQuoteParams> parameterType() {
        return CancellationQuoteParams.class;
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
    public ToolResult<CancellationQuoteToolResponse> execute(CancellationQuoteParams params, UserPrincipal user) {
        CancellationQuoteResponse quote = bookingService.getCancellationQuote(
                params.bookingId(),
                user != null ? user.getUserId() : null
        );
        CancellationQuoteToolResponse resp = new CancellationQuoteToolResponse(
                params.bookingId(),
                quote.refundAmount() != null ? quote.refundAmount().doubleValue() : 0.0,
                quote.policyExplanation() != null ? quote.policyExplanation() : "FLEXIBLE"
        );
        return ToolResult.ok(resp);
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(CancellationQuoteParams.class);
    }

    @Override
    public JsonNode responseSchema() {
        return jsonSchemaService.generate(CancellationQuoteToolResponse.class);
    }
}

