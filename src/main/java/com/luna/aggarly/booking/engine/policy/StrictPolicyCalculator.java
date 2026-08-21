package com.luna.aggarly.booking.engine.policy;

import com.luna.aggarly.booking.engine.CancellationPolicyCalculator;
import com.luna.aggarly.property.entity.enums.CancellationPolicyType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
public class StrictPolicyCalculator implements CancellationPolicyCalculator {

    @Override
    public CancellationPolicyType supports() {
        return CancellationPolicyType.STRICT;
    }

    @Override
    public BigDecimal calculateRefundPercentage(LocalDate checkIn, LocalDate cancellationDate) {
        long daysUntilCheckIn = ChronoUnit.DAYS.between(cancellationDate, checkIn);
        if (daysUntilCheckIn >= 14) {
            return BigDecimal.valueOf(50);
        }
        return BigDecimal.ZERO;
    }
}
