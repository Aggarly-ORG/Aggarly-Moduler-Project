package com.luna.aggarly.payment.controller;

import com.luna.aggarly.payment.gateway.GatewayEvent;
import com.luna.aggarly.payment.gateway.PaymentGateway;
import com.luna.aggarly.payment.gateway.PaymentGatewayFactory;
import com.luna.aggarly.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeWebhookControllerTest {

    @Mock
    private PaymentGatewayFactory gatewayFactory;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private StripeWebhookController stripeWebhookController;

    @Test
    void testHandleWebhook_Success() {
        String payload = "{\"id\":\"evt_test\",\"type\":\"payment_intent.succeeded\"}";
        String signature = "t=123,v1=mock_signature";
        GatewayEvent mockEvent = GatewayEvent.builder()
                .eventId("evt_test")
                .eventType("payment_intent.succeeded")
                .payload(payload)
                .build();

        when(gatewayFactory.getDefaultGateway()).thenReturn(paymentGateway);
        when(paymentGateway.verifyWebhookSignature(payload, signature)).thenReturn(mockEvent);

        ResponseEntity<Void> response = stripeWebhookController.handleWebhook(payload, signature);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(paymentService).handleWebhookEvent(mockEvent);
    }
}
