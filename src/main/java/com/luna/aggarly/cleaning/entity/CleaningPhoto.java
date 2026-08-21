package com.luna.aggarly.cleaning.entity;

import com.luna.aggarly.cleaning.entity.enums.CleaningPhotoType;
import com.luna.aggarly.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

@Entity
@Table(name = "cleaning_photos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("is_deleted=false")
@SQLDelete(sql = "UPDATE cleaning_photos SET is_deleted = true WHERE id = ?")
public class CleaningPhoto extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cleaning_task_id", nullable = false)
    private CleaningTask cleaningTask;

    @Column(name = "room_name", length = 100)
    private String roomName;

    @Enumerated(EnumType.STRING)
    @Column(name = "photo_type", nullable = false, length = 20)
    private CleaningPhotoType photoType;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;
}
