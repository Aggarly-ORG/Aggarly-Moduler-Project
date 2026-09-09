package com.luna.aggarly.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDetailResponse {
    private PaymentResponse payment;
    private String propertyTitle;
    private String propertyLocation;
    private String hostDisplayName;
    private String guestEmail;
    private List<PaymentAttemptResponse> attempts;
    private List<RefundResponse> refunds;
}
