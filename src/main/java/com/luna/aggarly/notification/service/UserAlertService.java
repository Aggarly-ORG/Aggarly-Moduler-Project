package com.luna.aggarly.notification.service;

import com.luna.aggarly.notification.dto.request.CreateUserAlertRequest;
import com.luna.aggarly.notification.dto.response.UserAlertResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface UserAlertService {

    UserAlertResponse createAlert(UUID userId, CreateUserAlertRequest request);

    List<UserAlertResponse> getUserAlerts(UUID userId);

    void deleteAlert(UUID alertId, UUID userId);

    void evaluatePriceChange(UUID propertyId, BigDecimal newPrice, String propertyTitle);
}
