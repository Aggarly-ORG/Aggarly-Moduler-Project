package com.luna.aggarly.aiagent.tool.booking.record;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.UUID;

public record CreateBookingResponse(
        @JsonPropertyDescription("The unique identifier generated for the new booking.")
        UUID bookingId,

        @JsonPropertyDescription("Current status of the reservation (e.g. PENDING_PAYMENT, CONFIRMED).")
        String status,

        @JsonPropertyDescription("Total calculated price for the stay.")
        double totalPrice,

        @JsonPropertyDescription("Total calculated price alias.")
        double totalAmount,

        @JsonPropertyDescription("Currency symbol or code (e.g. EUR, USD, €).")
        String currency,

        @JsonPropertyDescription("Stripe PaymentIntent client_secret used by frontend Stripe.js to complete the payment.")
        String clientSecret
) {}
