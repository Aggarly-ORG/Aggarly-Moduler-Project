package com.luna.aggarly.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @Size(max = 50, message = "Card brand must not exceed 50 characters")
    private String cardBrand;

    @NotBlank(message = "Last four digits are required")
    @Pattern(regexp = "^\\d{4}$", message = "Last four must be exactly 4 numeric digits")
    private String lastFour;

    @NotNull(message = "Expiration month is required")
    @Min(value = 1, message = "Expiration month must be between 1 and 12")
    @Max(value = 12, message = "Expiration month must be between 1 and 12")
    private Integer expMonth;

    @NotNull(message = "Expiration year is required")
    @Min(value = 2024, message = "Expiration year must be current or future year")
    private Integer expYear;

    @Size(max = 100, message = "Cardholder name must not exceed 100 characters")
    private String cardholderName;

    private boolean isDefault;
}
