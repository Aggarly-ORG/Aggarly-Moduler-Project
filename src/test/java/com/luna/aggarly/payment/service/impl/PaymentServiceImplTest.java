package com.luna.aggarly.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.payment.dto.PaymentIntentResponse;
import com.luna.aggarly.payment.entity.Payment;
import com.luna.aggarly.payment.entity.enums.PaymentStatus;
import com.luna.aggarly.payment.gateway.GatewayPaymentResult;
import com.luna.aggarly.payment.gateway.PaymentGateway;
import com.luna.aggarly.payment.gateway.PaymentGatewayFactory;
import com.luna.aggarly.payment.repository.IdempotencyRecordRepository;
import com.luna.aggarly.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private IdempotencyRecordRepository idempotencyRecordRepository;
    @Mock
    private PaymentGatewayFactory gatewayFactory;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        // leniency for tests that don't use it
    }

    @Test
    void createPaymentIntent_Success() {
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");
        String currency = "USD";
        String idempotencyKey = "key";

        when(idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(gatewayFactory.getDefaultGateway()).thenReturn(paymentGateway);
        when(paymentGateway.createPaymentIntent(bookingId, userId, amount, currency, idempotencyKey))
                .thenReturn(GatewayPaymentResult.builder()
                        .gatewayPaymentIntentId("pi_123")
                        .clientSecret("secret")
                        .build());
        
        Payment savedPayment = new Payment();
        savedPayment.setId(UUID.randomUUID());
        savedPayment.setBookingId(bookingId);
        savedPayment.setUserId(userId);
        savedPayment.setAmount(amount);
        savedPayment.setCurrency(currency);
        savedPayment.setStatus(PaymentStatus.CREATED);
        
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        PaymentIntentResponse response = paymentService.createPaymentIntent(bookingId, userId, amount, currency, idempotencyKey);

        assertNotNull(response);
        assertEquals(savedPayment.getId(), response.getPaymentId());
        assertEquals("secret", response.getClientSecret());
        assertEquals(amount, response.getAmount());
        assertEquals(currency, response.getCurrency());
        
        verify(paymentRepository, times(1)).save(any(Payment.class));
    }
}
