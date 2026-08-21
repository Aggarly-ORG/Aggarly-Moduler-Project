package com.luna.aggarly.scheduler.operation.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.luna.aggarly.payment.dto.EarningsSummaryResponse;
import com.luna.aggarly.payment.service.PaymentService;
import com.luna.aggarly.scheduler.operation.WorkflowOperation;
import com.luna.aggarly.scheduler.workflow.ExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class HostEarningsOperation implements WorkflowOperation {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "host.earningsReport";
    }

    @Override
    public String description() {
        return "Retrieves host revenue and earnings breakdown for a given period and currency.";
    }

    @Override
    public JsonNode parameterSchema() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        ObjectNode props = root.putObject("properties");
        props.putObject("currency").put("type", "string");
        return root;
    }

    @Override
    public JsonNode responseSchema() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        ObjectNode props = root.putObject("properties");
        props.putObject("totalGrossEarnings").put("type", "number");
        props.putObject("totalPlatformFees").put("type", "number");
        props.putObject("totalNetEarnings").put("type", "number");
        props.putObject("totalPaidOut").put("type", "number");
        props.putObject("pendingPayouts").put("type", "number");
        props.putObject("currency").put("type", "string");
        return root;
    }

    @Override
    public Object execute(Object arguments, ExecutionContext context) {
        String currency = "USD";
        if (arguments instanceof Map<?, ?> map && map.containsKey("currency") && map.get("currency") != null) {
            currency = String.valueOf(map.get("currency"));
        }
        log.info("Executing host.earningsReport for user {} (currency: {})", context.userId(), currency);
        EarningsSummaryResponse summary = paymentService.getEarningsSummary(currency);
        return objectMapper.convertValue(summary, Map.class);
    }
}
