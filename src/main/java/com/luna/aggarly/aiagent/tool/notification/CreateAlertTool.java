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
public class CreateAlertTool implements Tool<CreateAlertTool.CreateAlertRequest, String> {

    private final UserAlertService userAlertService;

    public record CreateAlertRequest(
            UUID propertyId,
            String city,
            Double targetPrice
    ) {}

    @Override
    public String name() {
        return "notification.createAlert";
    }

    @Override
    public String description() {
        return "Set up an alert notification for property availability or price changes.";
    }

    @Override
    public Class<CreateAlertRequest> parameterType() {
        return CreateAlertRequest.class;
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
    public ToolResult<String> execute(CreateAlertRequest params, UserPrincipal user) {
        try {
            if (user == null || user.getUserId() == null) {
                return ToolResult.failed("AUTH_REQUIRED", "User must be authenticated to create alerts.");
            }
            UUID userId = user.getUserId();
            BigDecimal price = params.targetPrice() != null ? BigDecimal.valueOf(params.targetPrice()) : null;

            UserAlertResponse alert = userAlertService.createAlert(userId, new CreateUserAlertRequest(
                    params.propertyId() != null ? AlertWatchType.PRICE_DROP : AlertWatchType.AVAILABILITY_OPEN,
                    params.propertyId(),
                    params.city(),
                    price
            ));

            return ToolResult.ok("Alert successfully created! Reference ID: " + alert.id());
        } catch (Exception e) {
            log.error("Failed to create alert in tool", e);
            return ToolResult.failed("ALERT_CREATION_FAILED", e.getMessage());
        }
    }
}
