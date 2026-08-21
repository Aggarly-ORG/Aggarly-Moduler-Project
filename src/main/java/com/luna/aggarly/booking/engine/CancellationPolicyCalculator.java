package com.luna.aggarly.booking.engine;

import com.luna.aggarly.property.entity.enums.CancellationPolicyType;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface CancellationPolicyCalculator {

    CancellationPolicyType supports();

    BigDecimal calculateRefundPercentage(LocalDate checkIn, LocalDate cancellationDate);
}
