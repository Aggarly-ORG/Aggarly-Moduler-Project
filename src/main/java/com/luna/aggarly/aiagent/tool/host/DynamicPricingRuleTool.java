package com.luna.aggarly.aiagent.tool.host;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.pricing.dto.PricingRuleRequest;
import com.luna.aggarly.pricing.dto.PricingRuleResponse;
import com.luna.aggarly.pricing.service.PricingRuleService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DynamicPricingRuleTool implements Tool<DynamicPricingRuleTool.CreateRuleParams, PricingRuleResponse> {

    public record CreateRuleParams(
            UUID propertyId,
            PricingRuleRequest request
    ) {}

    private final PricingRuleService pricingRuleService;

    @Override
    public String name() {
        return "host.createPricingRule";
    }

    @Override
    public String description() {
        return "Create dynamic pricing rules (weekend surge, seasonal adjustment, length-of-stay discount) for a property.";
    }

    @Override
    public Class<CreateRuleParams> parameterType() {
        return CreateRuleParams.class;
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    @Override
    public ToolResult<PricingRuleResponse> execute(CreateRuleParams params, UserPrincipal user) {
        PricingRuleResponse response = pricingRuleService.createRule(params.propertyId(), params.request());
        return ToolResult.ok(response);
    }
}
