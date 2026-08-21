package com.luna.aggarly.user.service;

import com.luna.aggarly.user.dto.SavePaymentMethodRequest;
import com.luna.aggarly.user.dto.UserPaymentMethodResponse;

import java.util.List;
import java.util.UUID;

public interface UserPaymentMethodService {
    List<UserPaymentMethodResponse> getUserPaymentMethods(UUID userId);
    UserPaymentMethodResponse savePaymentMethod(UUID userId, SavePaymentMethodRequest request);
    void deletePaymentMethod(UUID userId, UUID paymentMethodId);
    void setDefaultPaymentMethod(UUID userId, UUID paymentMethodId);
}
