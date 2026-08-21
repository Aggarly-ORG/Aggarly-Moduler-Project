package com.luna.aggarly.booking.engine;

import com.luna.aggarly.property.entity.enums.CancellationPolicyType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CancellationPolicyResolver {

    private final List<CancellationPolicyCalculator> calculators;

    private Map<CancellationPolicyType, CancellationPolicyCalculator> byType;

    public BigDecimal resolveRefundPercentage(CancellationPolicyType policyType,
                                               LocalDate checkIn, LocalDate cancellationDate) {
        if (byType == null) {
            byType = calculators.stream()
                    .collect(Collectors.toMap(CancellationPolicyCalculator::supports, c -> c));
        }
        CancellationPolicyCalculator calculator = byType.get(policyType);
        if (calculator == null) {
            return BigDecimal.ZERO;
        }
        return calculator.calculateRefundPercentage(checkIn, cancellationDate);
    }
}
