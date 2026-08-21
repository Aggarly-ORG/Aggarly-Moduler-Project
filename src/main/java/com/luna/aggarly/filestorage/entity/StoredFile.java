package com.luna.aggarly.filestorage.entity;

import com.luna.aggarly.common.entity.BaseEntity;
import com.luna.aggarly.filestorage.entity.enums.FileStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stored_files")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE stored_files SET is_deleted = true, deleted_at = CURRENT_TIMESTAMP, status = 'DELETED' WHERE id=?")
@SQLRestriction("is_deleted = false")
public class StoredFile extends BaseEntity {

    @Column(name = "object_key", nullable = false, unique = true, length = 1024)
    private String objectKey;

    @Column(nullable = false, length = 100)
    private String bucket;

    @Column(name = "original_filename", nullable = false, length = 500)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(length = 20)
    private String extension;

    @Column(nullable = false)
    private Long size;

    @Column
    private String checksum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private FileStatus status = FileStatus.PENDING;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Column(name = "activated_at")
    private Instant activatedAt;
    
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    protected void onPrePersist() {
        if (uploadedAt == null) {
            uploadedAt = Instant.now();
        }
    }
}
