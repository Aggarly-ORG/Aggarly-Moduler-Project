package com.luna.aggarly.user.dto;

import com.luna.aggarly.user.entity.UserPaymentMethod;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPaymentMethodResponse {

    private UUID id;
    private String stripePaymentMethodId;
    private String brand;
    private String last4;
    private String expiry;
    private Integer expMonth;
    private Integer expYear;
    private String cardholderName;
    private boolean isDefault;
    private Instant createdAt;

    public static UserPaymentMethodResponse fromEntity(UserPaymentMethod entity) {
        String expiryFormatted = String.format("%02d/%s", 
                entity.getExpMonth() != null ? entity.getExpMonth() : 12,
                entity.getExpYear() != null ? String.valueOf(entity.getExpYear()).substring(Math.max(0, String.valueOf(entity.getExpYear()).length() - 2)) : "28");

        return UserPaymentMethodResponse.builder()
                .id(entity.getId())
                .stripePaymentMethodId(entity.getStripePaymentMethodId())
                .brand(entity.getCardBrand())
                .last4(entity.getLastFour())
                .expiry(expiryFormatted)
                .expMonth(entity.getExpMonth())
                .expYear(entity.getExpYear())
                .cardholderName(entity.getCardholderName())
                .isDefault(entity.isDefault())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
