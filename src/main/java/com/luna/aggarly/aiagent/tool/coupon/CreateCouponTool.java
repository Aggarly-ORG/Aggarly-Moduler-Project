package com.luna.aggarly.aiagent.tool.coupon;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.pricing.dto.CouponRequest;
import com.luna.aggarly.pricing.dto.CouponResponse;
import com.luna.aggarly.pricing.service.CouponService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class CreateCouponTool implements Tool<CreateCouponTool.Params, CouponResponse> {

    public record Params(
            String code,
            String adjustmentType,
            BigDecimal adjustmentValue,
            Instant expiresAt,
            Integer maxRedemptions,
            BigDecimal minSubtotal
    ) {}

    private final CouponService couponService;

    @Override
    public String name() {
        return "coupon.create";
    }

    @Override
    public String description() {
        return "Create a new discount coupon code for administrative promotional campaigns.";
    }

    @Override
    public Class<Params> parameterType() {
        return Params.class;
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
    public ToolResult<CouponResponse> execute(Params params, UserPrincipal user) {
        CouponRequest request = new CouponRequest(
                params.code(),
                params.adjustmentType(),
                params.adjustmentValue(),
                params.expiresAt(),
                params.maxRedemptions(),
                params.minSubtotal()
        );
        CouponResponse response = couponService.createCoupon(request);
        return ToolResult.ok(response);
    }
}
