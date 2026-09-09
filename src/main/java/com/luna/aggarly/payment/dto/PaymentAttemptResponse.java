package com.luna.aggarly.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentAttemptResponse {
    private UUID id;
    private String result;
    private String gatewayErrorCode;
    private Integer radarRiskScore;
    private String radarRiskLevel;
    private String threeDSecureStatus;
    private String ipOriginCountry;
    private Instant createdAt;
}
