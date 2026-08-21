package com.luna.aggarly.payment.entity.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentStatusTest {

    @Test
    void testValidTransitions() {
        assertTrue(PaymentStatus.CREATED.canTransitionTo(PaymentStatus.PENDING));
        assertTrue(PaymentStatus.CREATED.canTransitionTo(PaymentStatus.CANCELLED));
        assertTrue(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.SUCCEEDED));
        assertTrue(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.FAILED));
        assertTrue(PaymentStatus.SUCCEEDED.canTransitionTo(PaymentStatus.PARTIALLY_REFUNDED));
        assertTrue(PaymentStatus.SUCCEEDED.canTransitionTo(PaymentStatus.REFUNDED));
        assertTrue(PaymentStatus.PARTIALLY_REFUNDED.canTransitionTo(PaymentStatus.REFUNDED));
    }

    @Test
    void testInvalidTransitions() {
        assertFalse(PaymentStatus.CREATED.canTransitionTo(PaymentStatus.SUCCEEDED));
        assertFalse(PaymentStatus.SUCCEEDED.canTransitionTo(PaymentStatus.CREATED));
        assertFalse(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.CREATED));
        assertFalse(PaymentStatus.CANCELLED.canTransitionTo(PaymentStatus.PENDING));
        assertFalse(PaymentStatus.REFUNDED.canTransitionTo(PaymentStatus.PARTIALLY_REFUNDED));
    }
}
