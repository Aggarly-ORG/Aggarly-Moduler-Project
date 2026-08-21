package com.luna.aggarly.payment.mock;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/mock/stripe")
public class StripeMockController {

    @PostMapping("/v1/payment_intents")
    public ResponseEntity<Map<String, Object>> createPaymentIntent(@RequestParam Map<String, String> allParams) {
        String intentId = "pi_" + UUID.randomUUID().toString().replace("-", "");
        return ResponseEntity.ok(Map.of(
                "id", intentId,
                "object", "payment_intent",
                "amount", allParams.getOrDefault("amount", "1000"),
                "currency", allParams.getOrDefault("currency", "usd"),
                "status", "requires_payment_method",
                "client_secret", intentId + "_secret_" + UUID.randomUUID().toString().replace("-", "")
        ));
    }

    @PostMapping("/v1/payment_intents/{intentId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelPaymentIntent(@PathVariable String intentId) {
        return ResponseEntity.ok(Map.of(
                "id", intentId,
                "object", "payment_intent",
                "status", "canceled"
        ));
    }

    @PostMapping("/v1/refunds")
    public ResponseEntity<Map<String, Object>> createRefund(@RequestParam Map<String, String> allParams) {
        String refundId = "re_" + UUID.randomUUID().toString().replace("-", "");
        return ResponseEntity.ok(Map.of(
                "id", refundId,
                "object", "refund",
                "amount", allParams.getOrDefault("amount", "1000"),
                "status", "succeeded",
                "payment_intent", allParams.get("payment_intent")
        ));
    }

    @PostMapping("/v1/customers")
    public ResponseEntity<Map<String, Object>> createCustomer(@RequestParam Map<String, String> allParams) {
        String customerId = "cus_" + UUID.randomUUID().toString().replace("-", "");
        return ResponseEntity.ok(Map.of(
                "id", customerId,
                "object", "customer",
                "email", allParams.getOrDefault("email", "")
        ));
    }
    
    @GetMapping("/v1/customers")
    public ResponseEntity<Map<String, Object>> searchCustomers(@RequestParam Map<String, String> allParams) {
        return ResponseEntity.ok(Map.of(
                "object", "list",
                "data", java.util.List.of()
        ));
    }
}
