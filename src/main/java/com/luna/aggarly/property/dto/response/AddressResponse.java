package com.luna.aggarly.property.dto.response;

import java.util.UUID;

public record AddressResponse(
        UUID id,
        String street,
        String city,
        String state,
        String country,
        String zipCode
) {
}
