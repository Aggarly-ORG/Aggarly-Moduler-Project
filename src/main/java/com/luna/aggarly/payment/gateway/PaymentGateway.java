package com.luna.aggarly.payment.gateway;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public interface PaymentGateway {
    GatewayPaymentResult createPaymentIntent(UUID bookingId, UUID userId, BigDecimal amount, String currency, String idempotencyKey);
    
    GatewayPaymentResult confirmPaymentIntent(String gatewayIntentId, String paymentMethodId);

    void cancelPaymentIntent(String gatewayIntentId, String idempotencyKey);
    
    GatewayRefundResult createRefund(String gatewayIntentId, BigDecimal amount, String idempotencyKey);
    
    String createOrGetCustomer(String email, String name);
    
    GatewayEvent verifyWebhookSignature(String payload, String signature);
    
    Set<PaymentCapability> getCapabilities();
    
    String getProviderName();
}
