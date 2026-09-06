package com.luna.aggarly.aiagent.tool.coupon;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.pricing.dto.CouponValidationResponse;
import com.luna.aggarly.pricing.service.CouponService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ValidateCouponTool implements Tool<ValidateCouponTool.Params, CouponValidationResponse> {

    public record Params(
            String code,
            BigDecimal subtotal
    ) {}

    private final CouponService couponService;

    @Override
    public String name() {
        return "coupon.validate";
    }

    @Override
    public String description() {
        return "Validate a promo coupon code and calculate applicable discount for a given subtotal.";
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
    public ToolResult<CouponValidationResponse> execute(Params params, UserPrincipal user) {
        UUID userId = user != null ? user.getUserId() : null;
        CouponValidationResponse response = couponService.validateCoupon(params.code(), params.subtotal(), userId);
        return ToolResult.ok(response);
    }
}
