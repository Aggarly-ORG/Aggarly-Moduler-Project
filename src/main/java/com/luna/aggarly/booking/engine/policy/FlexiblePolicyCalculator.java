package com.luna.aggarly.booking.engine.policy;

import com.luna.aggarly.booking.engine.CancellationPolicyCalculator;
import com.luna.aggarly.property.entity.enums.CancellationPolicyType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
public class FlexiblePolicyCalculator implements CancellationPolicyCalculator {

    @Override
    public CancellationPolicyType supports() {
        return CancellationPolicyType.FLEXIBLE;
    }

    @Override
    public BigDecimal calculateRefundPercentage(LocalDate checkIn, LocalDate cancellationDate) {
        long daysUntilCheckIn = ChronoUnit.DAYS.between(cancellationDate, checkIn);
        return daysUntilCheckIn >= 1 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
    }
}
