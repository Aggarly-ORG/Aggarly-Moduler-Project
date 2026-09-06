package com.luna.aggarly.aiagent.tool.payment;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.payment.dto.PaymentDetailResponse;
import com.luna.aggarly.payment.service.PaymentService;
import com.luna.aggarly.user.security.UserPrincipal;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaymentStatusTool implements Tool<PaymentStatusTool.Params, PaymentDetailResponse> {

    public record Params(
            @NotNull
            @JsonPropertyDescription("The unique identifier (UUID) of the booking whose payment status is being queried.")
            UUID bookingId
    ) {}

    private final PaymentService paymentService;
    private final JsonSchemaService jsonSchemaService;

    @Override
    public String name() {
        return "payment.status";
    }

    @Override
    public String description() {
        return "Fetch payment status, transaction reference, amount, and gateway status for a booking.";
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
    public ToolResult<PaymentDetailResponse> execute(Params params, UserPrincipal user) {
        if (params == null || params.bookingId() == null) {
            return ToolResult.failed("INVALID_PARAMS", "bookingId is required to fetch payment status.");
        }
        PaymentDetailResponse response = paymentService.getPaymentDetails(params.bookingId());
        return ToolResult.ok(response);
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(Params.class);
    }

    @Override
    public JsonNode responseSchema() {
        return jsonSchemaService.generate(PaymentDetailResponse.class);
    }
}
