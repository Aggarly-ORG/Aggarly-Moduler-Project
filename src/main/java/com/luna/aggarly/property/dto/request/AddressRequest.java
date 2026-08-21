package com.luna.aggarly.property.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank(message = "Street is required")
        @Size(max = 255)
        String street,

        @NotBlank(message = "City is required")
        @Size(max = 100)
        String city,

        @Size(max = 100)
        String state,

        @NotBlank(message = "Country is required")
        @Size(max = 100)
        String country,

        @Size(max = 20)
        String zipCode
) {
}
