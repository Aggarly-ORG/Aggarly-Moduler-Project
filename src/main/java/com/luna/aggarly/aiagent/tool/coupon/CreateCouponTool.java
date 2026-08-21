package com.luna.aggarly.aiagent.tool.coupon;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.pricing.dto.CouponRequest;
import com.luna.aggarly.pricing.dto.CouponResponse;
import com.luna.aggarly.pricing.service.CouponService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CreateCouponTool implements Tool<CouponRequest, CouponResponse> {

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
    public Class<CouponRequest> parameterType() {
        return CouponRequest.class;
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
    public ToolResult<CouponResponse> execute(CouponRequest params, UserPrincipal user) {
        CouponResponse response = couponService.createCoupon(params);
        return ToolResult.ok(response);
    }
}
