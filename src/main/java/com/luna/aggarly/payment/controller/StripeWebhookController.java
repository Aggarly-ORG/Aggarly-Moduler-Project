package com.luna.aggarly.payment.controller;

import com.luna.aggarly.payment.gateway.GatewayEvent;
import com.luna.aggarly.payment.gateway.PaymentGatewayFactory;
import com.luna.aggarly.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller receiving asynchronous webhook notifications from Stripe.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payments/webhook")
@RequiredArgsConstructor
@Tag(name = "Payments Webhook", description = "Stripe Async Webhook Ingestion API")
public class StripeWebhookController {

    private final PaymentGatewayFactory gatewayFactory;
    private final PaymentService paymentService;

    @PostMapping
    @Operation(summary = "Ingest Stripe webhook events (Public / Signature-Verified)")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature) {
        try {
            GatewayEvent event = gatewayFactory.getDefaultGateway().verifyWebhookSignature(payload, signature);
            paymentService.handleWebhookEvent(event);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid webhook signature: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error processing webhook", e);
            return ResponseEntity.ok().build();
        }
    }
}
