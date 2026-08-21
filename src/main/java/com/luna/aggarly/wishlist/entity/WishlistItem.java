package com.luna.aggarly.wishlist.entity;

import com.luna.aggarly.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "wishlist_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("is_deleted=false")
@SQLDelete(sql = "UPDATE wishlist_items SET is_deleted = true, deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
public class WishlistItem extends BaseEntity {

    @Column(name = "wishlist_id", nullable = false)
    private UUID wishlistId;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;
}
