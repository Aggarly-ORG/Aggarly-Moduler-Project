package com.luna.aggarly.payment.mapper;

import com.luna.aggarly.payment.dto.PaymentAttemptResponse;
import com.luna.aggarly.payment.dto.PaymentResponse;
import com.luna.aggarly.payment.dto.RefundResponse;
import com.luna.aggarly.payment.entity.Payment;
import com.luna.aggarly.payment.entity.PaymentAttempt;
import com.luna.aggarly.payment.entity.Refund;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentMapper {
    PaymentResponse toPaymentResponse(Payment payment);

    @Mapping(target = "refundId", source = "id")
    RefundResponse toRefundResponse(Refund refund);
    
    PaymentAttemptResponse toPaymentAttemptResponse(PaymentAttempt attempt);
}
