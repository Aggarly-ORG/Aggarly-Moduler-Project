package com.luna.aggarly.aiagent.tool.notification;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
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
public class CreateAlertTool implements Tool<CreateAlertTool.Params, String> {

    private final UserAlertService userAlertService;

    public record Params(
            @JsonPropertyDescription("Alert kind: PRICE_DROP (notify when nightly price falls below targetPrice), " +
                    "AVAILABILITY_OPEN (notify when dates become bookable) or NEW_LISTING. " +
                    "Defaults to PRICE_DROP when targetPrice is set, otherwise AVAILABILITY_OPEN.")
            String type,

            @JsonPropertyDescription("UUID of the property to watch (required for PRICE_DROP).")
            UUID propertyId,

            @JsonPropertyDescription("Optional city name for city-wide availability or new-listing watches.")
            String city,

            @JsonPropertyDescription("Target nightly price for PRICE_DROP alerts.")
            Double targetPrice
    ) {}

    @Override
    public String name() {
        return "notification.createAlert";
    }

    @Override
    public String description() {
        return "Set up a notification alert: track price drops below a target price, watch for availability " +
                "opening on a property or in a city, or get notified about new listings.";
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
        return false;
    }

    @Override
    public ToolResult<String> execute(Params params, UserPrincipal user) {
        try {
            if (user == null || user.getUserId() == null) {
                return ToolResult.failed("AUTH_REQUIRED", "User must be authenticated to create alerts.");
            }

            AlertWatchType watchType = resolveWatchType(params);
            if (watchType == AlertWatchType.PRICE_DROP && (params == null || params.propertyId() == null)) {
                return ToolResult.failed("INVALID_ALERT_REQUEST",
                        "PRICE_DROP alerts require a propertyId to watch.");
            }
            if (params != null && params.propertyId() == null && (params.city() == null || params.city().isBlank())) {
                return ToolResult.failed("INVALID_ALERT_REQUEST",
                        "Provide at least a propertyId or a city to watch.");
            }

            BigDecimal price = (params != null && params.targetPrice() != null) ? BigDecimal.valueOf(params.targetPrice()) : null;
            UserAlertResponse alert = userAlertService.createAlert(user.getUserId(), new CreateUserAlertRequest(
                    watchType,
                    params != null ? params.propertyId() : null,
                    params != null ? params.city() : null,
                    price
            ));

            String detail = switch (watchType) {
                case PRICE_DROP -> String.format("price tracking activated on property %s (target: %s)",
                        params != null ? params.propertyId() : null, price != null ? price.toPlainString() : "any drop");
                case AVAILABILITY_OPEN -> (params != null && params.propertyId() != null)
                        ? "availability watch activated on property " + params.propertyId()
                        : "availability watch activated for city '" + (params != null ? params.city() : "") + "'";
                case NEW_LISTING -> (params != null && params.city() != null)
                        ? "new listing watch activated for city '" + params.city() + "'"
                        : "new listing watch activated";
            };
            return ToolResult.ok("Alert successfully created (" + detail + "). Reference ID: " + alert.id());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid alert type requested: {}", params != null ? params.type() : null, e);
            return ToolResult.failed("INVALID_ALERT_TYPE",
                    "Unknown alert type '" + (params != null ? params.type() : "") + "'. Use PRICE_DROP, AVAILABILITY_OPEN or NEW_LISTING.");
        } catch (Exception e) {
            log.error("Failed to create alert in tool", e);
            return ToolResult.failed("ALERT_CREATION_FAILED", e.getMessage());
        }
    }

    private AlertWatchType resolveWatchType(Params params) {
        if (params != null && params.type() != null && !params.type().isBlank()) {
            return AlertWatchType.valueOf(params.type().trim().toUpperCase());
        }
        return (params != null && params.targetPrice() != null) ? AlertWatchType.PRICE_DROP : AlertWatchType.AVAILABILITY_OPEN;
    }
}
