package com.luna.aggarly.aiagent.tool.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.aiagent.tool.payment.record.PaymentStatusParams;
import com.luna.aggarly.payment.dto.PaymentDetailResponse;
import com.luna.aggarly.payment.service.PaymentService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaymentStatusTool implements Tool<PaymentStatusParams, PaymentDetailResponse> {

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
    public Class<PaymentStatusParams> parameterType() {
        return PaymentStatusParams.class;
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
    public ToolResult<PaymentDetailResponse> execute(PaymentStatusParams params, UserPrincipal user) {
        if (params == null || params.bookingId() == null) {
            return ToolResult.failed("INVALID_PARAMS", "bookingId is required to fetch payment status.");
        }
        PaymentDetailResponse response = paymentService.getPaymentDetails(params.bookingId());
        return ToolResult.ok(response);
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(PaymentStatusParams.class);
    }

    @Override
    public JsonNode responseSchema() {
        return jsonSchemaService.generate(PaymentDetailResponse.class);
    }
}
