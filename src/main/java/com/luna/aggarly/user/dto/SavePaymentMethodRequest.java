package com.luna.aggarly.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavePaymentMethodRequest {

    @NotBlank(message = "Stripe payment method ID is required")
    private String stripePaymentMethodId;

    @NotBlank(message = "Card brand is required")
    private String cardBrand;

    @NotBlank(message = "Last four digits are required")
    private String lastFour;

    @NotNull(message = "Expiration month is required")
    private Integer expMonth;

    @NotNull(message = "Expiration year is required")
    private Integer expYear;

    private String cardholderName;

    private boolean isDefault;
}
