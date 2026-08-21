package com.luna.aggarly.aiagent.tool.notification;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.notification.dto.request.CreateUserAlertRequest;
import com.luna.aggarly.notification.dto.response.UserAlertResponse;
import com.luna.aggarly.notification.entity.enums.AlertWatchType;
import com.luna.aggarly.notification.service.UserAlertService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PriceTrackingTool implements Tool<PriceTrackingTool.PriceTrackingRequest, String> {

    private final UserAlertService userAlertService;

    public record PriceTrackingRequest(
            UUID propertyId,
            BigDecimal targetPrice
    ) {}

    @Override
    public String name() {
        return "notification.priceTracking";
    }

    @Override
    public String description() {
        return "Track price drops for a specific property and notify when price falls below target.";
    }

    @Override
    public Class<PriceTrackingRequest> parameterType() {
        return PriceTrackingRequest.class;
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
    public ToolResult<String> execute(PriceTrackingRequest params, UserPrincipal user) {
        try {
            if (user == null || user.getUserId() == null) {
                return ToolResult.failed("AUTH_REQUIRED", "User must be authenticated to track prices.");
            }
            UUID userId = user.getUserId();
            UserAlertResponse alert = userAlertService.createAlert(userId, new CreateUserAlertRequest(
                    AlertWatchType.PRICE_DROP,
                    params.propertyId(),
                    null,
                    params.targetPrice()
            ));

            return ToolResult.ok(String.format("Price tracking activated for property %s at target $%s (Alert ID: %s)",
                    params.propertyId(), params.targetPrice(), alert.id()));
        } catch (Exception e) {
            log.error("Failed to set up price tracking in tool", e);
            return ToolResult.failed("PRICE_TRACKING_FAILED", e.getMessage());
        }
    }
}
