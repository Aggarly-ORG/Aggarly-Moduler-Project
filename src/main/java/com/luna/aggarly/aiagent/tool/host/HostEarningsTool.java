package com.luna.aggarly.aiagent.tool.host;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.payment.dto.EarningsSummaryResponse;
import com.luna.aggarly.payment.service.PaymentService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HostEarningsTool implements Tool<String, EarningsSummaryResponse> {

    private final PaymentService paymentService;

    @Override
    public String name() {
        return "host.earnings";
    }

    @Override
    public String description() {
        return "Fetch host earnings summary, completed payouts, and pending revenue.";
    }

    @Override
    public Class<String> parameterType() {
        return String.class;
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ToolResult<EarningsSummaryResponse> execute(String currency, UserPrincipal user) {
        String targetCurrency = currency != null && !currency.isBlank() ? currency : "USD";
        EarningsSummaryResponse summary = paymentService.getEarningsSummary(targetCurrency);
        return ToolResult.ok(summary);
    }
}
