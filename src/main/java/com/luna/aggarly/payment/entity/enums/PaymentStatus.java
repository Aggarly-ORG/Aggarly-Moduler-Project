package com.luna.aggarly.payment.entity.enums;

public enum PaymentStatus {
    CREATED,
    PENDING,
    SUCCEEDED,
    FAILED,
    REFUNDED,
    PARTIALLY_REFUNDED,
    DISPUTED,
    CANCELLED;

    public boolean canTransitionTo(PaymentStatus target) {
        if (this == target) return true;
        
        return switch (this) {
            case CREATED -> target == PENDING || target == CANCELLED;
            case PENDING -> target == SUCCEEDED || target == FAILED || target == CANCELLED;
            case SUCCEEDED -> target == REFUNDED || target == PARTIALLY_REFUNDED || target == DISPUTED;
            case FAILED -> target == PENDING || target == CANCELLED;
            case CANCELLED -> false;
            case REFUNDED -> target == DISPUTED;
            case PARTIALLY_REFUNDED -> target == REFUNDED || target == PARTIALLY_REFUNDED || target == DISPUTED;
            case DISPUTED -> target == SUCCEEDED;
        };
    }
}