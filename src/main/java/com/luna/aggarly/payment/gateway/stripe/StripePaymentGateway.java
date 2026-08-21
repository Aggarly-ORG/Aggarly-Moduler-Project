package com.luna.aggarly.payment.gateway.stripe;

import com.luna.aggarly.payment.config.StripeConfig;
import com.luna.aggarly.payment.gateway.GatewayEvent;
import com.luna.aggarly.payment.gateway.GatewayPaymentResult;
import com.luna.aggarly.payment.gateway.GatewayRefundResult;
import com.luna.aggarly.payment.gateway.PaymentCapability;
import com.luna.aggarly.payment.gateway.PaymentGateway;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerSearchParams;
import com.stripe.param.PaymentIntentCancelParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class StripePaymentGateway implements PaymentGateway {

    private final StripeConfig stripeConfig;

    @Override
    public GatewayPaymentResult createPaymentIntent(UUID bookingId, UUID userId, BigDecimal amount, String currency, String idempotencyKey) {
        try {
            long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValue();
            
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency(currency)
                    .putMetadata("bookingId", bookingId.toString())
                    .putMetadata("userId", userId.toString())
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                                    .build()
                    )
                    .build();

            com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                    .setIdempotencyKey(idempotencyKey)
                    .build();

            PaymentIntent paymentIntent = PaymentIntent.create(params, requestOptions);

            return GatewayPaymentResult.builder()
                    .gatewayPaymentIntentId(paymentIntent.getId())
                    .gatewayCustomerId(paymentIntent.getCustomer())
                    .clientSecret(paymentIntent.getClientSecret())
                    .status(paymentIntent.getStatus())
                    .build();
        } catch (StripeException e) {
            log.error("Stripe error creating payment intent", e);
            throw new RuntimeException("Failed to create payment intent with Stripe", e);
        }
    }

    @Override
    public GatewayPaymentResult confirmPaymentIntent(String gatewayIntentId, String paymentMethodId) {
        try {
            PaymentIntent paymentIntent = PaymentIntent.retrieve(gatewayIntentId);
            if (!"succeeded".equals(paymentIntent.getStatus())) {
                String pm = resolvePaymentMethod(paymentMethodId);

                com.stripe.param.PaymentIntentConfirmParams params = com.stripe.param.PaymentIntentConfirmParams.builder()
                        .setPaymentMethod(pm)
                        .setReturnUrl("http://localhost:8081/oauth2-success.html")
                        .build();

                paymentIntent = paymentIntent.confirm(params);
                log.info("Stripe PaymentIntent {} confirmed successfully on Stripe with paymentMethod {}, status: {}",
                        gatewayIntentId, pm, paymentIntent.getStatus());
            }

            return GatewayPaymentResult.builder()
                    .gatewayPaymentIntentId(paymentIntent.getId())
                    .gatewayCustomerId(paymentIntent.getCustomer())
                    .clientSecret(paymentIntent.getClientSecret())
                    .status(paymentIntent.getStatus())
                    .build();
        } catch (StripeException e) {
            log.error("Stripe error confirming payment intent {}", gatewayIntentId, e);
            throw new RuntimeException("Failed to confirm payment intent with Stripe: " + e.getMessage(), e);
        }
    }

    private String resolvePaymentMethod(String input) {
        if (input == null || input.isBlank()) {
            return "pm_card_visa";
        }
        String clean = input.trim();

        // 1. Direct Stripe PaymentMethod or Token ID (e.g. pm_1N..., pm_card_mastercard, tok_1N...)
        if (clean.startsWith("pm_") || clean.startsWith("tok_")) {
            return clean;
        }

        // 2. Direct Raw Card Number (13 to 19 digits) - dynamically create Stripe PaymentMethod
        String digitsOnly = clean.replaceAll("[\\s-]", "");
        if (digitsOnly.matches("^\\d{13,19}$")) {
            try {
                com.stripe.param.PaymentMethodCreateParams params = com.stripe.param.PaymentMethodCreateParams.builder()
                        .setType(com.stripe.param.PaymentMethodCreateParams.Type.CARD)
                        .setCard(
                                com.stripe.param.PaymentMethodCreateParams.CardDetails.builder()
                                        .setNumber(digitsOnly)
                                        .setExpMonth(12L)
                                        .setExpYear(2030L)
                                        .setCvc("123")
                                        .build()
                        )
                        .build();
                com.stripe.model.PaymentMethod pm = com.stripe.model.PaymentMethod.create(params);
                log.info("Created dynamic Stripe PaymentMethod {} for card ending in {}",
                        pm.getId(), pm.getCard() != null ? pm.getCard().getLast4() : "????");
                return pm.getId();
            } catch (Exception e) {
                log.warn("Failed to create dynamic Stripe PaymentMethod for card: {}. Falling back to brand detection.", e.getMessage());
            }
        }

        // 3. Brand name or slug matching
        String lower = clean.toLowerCase();
        if (lower.contains("mastercard") || lower.contains("mc") || digitsOnly.startsWith("5")) {
            return "pm_card_mastercard";
        }
        if (lower.contains("amex") || lower.contains("american") || digitsOnly.startsWith("34") || digitsOnly.startsWith("37")) {
            return "pm_card_amex";
        }
        if (lower.contains("discover") || digitsOnly.startsWith("6")) {
            return "pm_card_discover";
        }
        if (lower.contains("jcb") || digitsOnly.startsWith("35")) {
            return "pm_card_jcb";
        }
        if (lower.contains("diners")) {
            return "pm_card_diners";
        }
        if (lower.contains("unionpay") || lower.contains("union")) {
            return "pm_card_unionpay";
        }
        if (lower.contains("debit")) {
            return "pm_card_visa_debit";
        }
        if (lower.contains("visa") || digitsOnly.startsWith("4")) {
            return "pm_card_visa";
        }

        return "pm_card_visa";
    }

    @Override
    public void cancelPaymentIntent(String gatewayIntentId, String idempotencyKey) {
        try {
            PaymentIntent paymentIntent = PaymentIntent.retrieve(gatewayIntentId);
            
            com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                    .setIdempotencyKey(idempotencyKey)
                    .build();
                    
            if (!"canceled".equals(paymentIntent.getStatus()) && !"succeeded".equals(paymentIntent.getStatus())) {
                paymentIntent.cancel(PaymentIntentCancelParams.builder().build(), requestOptions);
            }
        } catch (StripeException e) {
            log.error("Stripe error cancelling payment intent {}", gatewayIntentId, e);
            throw new RuntimeException("Failed to cancel payment intent with Stripe", e);
        }
    }

    @Override
    public GatewayRefundResult createRefund(String gatewayIntentId, BigDecimal amount, String idempotencyKey) {
        try {
            long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValue();
            
            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(gatewayIntentId)
                    .setAmount(amountInCents)
                    .build();

            com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                    .setIdempotencyKey(idempotencyKey)
                    .build();

            Refund refund = Refund.create(params, requestOptions);

            return GatewayRefundResult.builder()
                    .gatewayRefundId(refund.getId())
                    .status(refund.getStatus())
                    .build();
        } catch (StripeException e) {
            log.error("Stripe error creating refund for intent {}", gatewayIntentId, e);
            throw new RuntimeException("Failed to create refund with Stripe", e);
        }
    }

    @Override
    public String createOrGetCustomer(String email, String name) {
        try {
            CustomerSearchParams searchParams = CustomerSearchParams.builder()
                    .setQuery(String.format("email:'%s'", email))
                    .build();
            
            var searchResult = Customer.search(searchParams);
            if (!searchResult.getData().isEmpty()) {
                return searchResult.getData().get(0).getId();
            }

            CustomerCreateParams createParams = CustomerCreateParams.builder()
                    .setEmail(email)
                    .setName(name)
                    .build();
                    
            Customer customer = Customer.create(createParams);
            return customer.getId();
        } catch (StripeException e) {
            log.error("Stripe error creating/getting customer for email {}", email, e);
            throw new RuntimeException("Failed to create or get Stripe customer", e);
        }
    }

    @Override
    public GatewayEvent verifyWebhookSignature(String payload, String signature) {
        try {
            Event event = Webhook.constructEvent(payload, signature, stripeConfig.getWebhookSecret());
            return GatewayEvent.builder()
                    .eventId(event.getId())
                    .eventType(event.getType())
                    .payload(payload)
                    .build();
        } catch (SignatureVerificationException e) {
            log.error("Stripe webhook signature verification failed", e);
            throw new IllegalArgumentException("Invalid webhook signature", e);
        }
    }

    @Override
    public Set<PaymentCapability> getCapabilities() {
        return EnumSet.of(
                PaymentCapability.CARD_PAYMENTS,
                PaymentCapability.APPLE_PAY,
                PaymentCapability.GOOGLE_PAY,
                PaymentCapability.REFUNDS,
                PaymentCapability.PARTIAL_REFUNDS,
                PaymentCapability.DISPUTES
        );
    }

    @Override
    public String getProviderName() {
        return "stripe";
    }
}
