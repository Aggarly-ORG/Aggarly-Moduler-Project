package com.luna.aggarly.booking.engine.policy;

import com.luna.aggarly.booking.engine.CancellationPolicyCalculator;
import com.luna.aggarly.property.entity.enums.CancellationPolicyType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class NonRefundablePolicyCalculator implements CancellationPolicyCalculator {

    @Override
    public CancellationPolicyType supports() {
        return CancellationPolicyType.NON_REFUNDABLE;
    }

    @Override
    public BigDecimal calculateRefundPercentage(LocalDate checkIn, LocalDate cancellationDate) {
        return BigDecimal.ZERO;
    }
}
