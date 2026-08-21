package com.luna.aggarly.payment.entity;

import com.luna.aggarly.common.entity.BaseEntity;
import com.luna.aggarly.payment.entity.enums.AttemptResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

@Entity
@Table(name = "payment_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("is_deleted=false")
@SQLDelete(sql = "UPDATE payment_attempts SET is_deleted = true, deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
public class PaymentAttempt extends BaseEntity {

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20)
    private AttemptResult result;

    @Column(name = "gateway_error_code", length = 100)
    private String gatewayErrorCode;

    @Column(name = "gateway_error_message", length = 500)
    private String gatewayErrorMessage;
}