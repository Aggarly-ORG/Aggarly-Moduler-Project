package com.luna.aggarly.payment.dto;

import com.luna.aggarly.payment.entity.enums.RefundStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundResponse {
    private UUID refundId;
    private BigDecimal amount;
    private RefundStatus status;
}
