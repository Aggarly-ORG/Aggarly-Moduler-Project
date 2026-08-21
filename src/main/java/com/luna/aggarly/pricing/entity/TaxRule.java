package com.luna.aggarly.pricing.entity;

import com.luna.aggarly.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Entity
@Table(name = "tax_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE tax_rules SET is_deleted = true, deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("is_deleted = false")
public class TaxRule extends BaseEntity {

    @Column(nullable = false)
    private String region; // e.g. country or city code — matches Property.location

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal ratePercentage; // e.g. 8.00 for 8%

    @Column(nullable = false)
    private boolean active;
}
