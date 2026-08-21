package com.luna.aggarly.payment.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayPaymentResult {
    private String gatewayPaymentIntentId;
    private String gatewayCustomerId;
    private String clientSecret;
    private String status;
}
