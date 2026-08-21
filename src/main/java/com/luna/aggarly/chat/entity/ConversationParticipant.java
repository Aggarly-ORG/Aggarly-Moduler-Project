package com.luna.aggarly.chat.entity;

import com.luna.aggarly.chat.entity.enums.ParticipantRole;
import com.luna.aggarly.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversation_participants",
        uniqueConstraints = @UniqueConstraint(name = "uq_conversation_user", columnNames = {"conversation_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("is_deleted=false")
@SQLDelete(sql = "UPDATE conversation_participants SET is_deleted = true WHERE id = ?")
public class ConversationParticipant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    @Builder.Default
    private ParticipantRole role = ParticipantRole.MEMBER;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    @Column(name = "unread_count", nullable = false)
    @Builder.Default
    private int unreadCount = 0;

    @Column(name = "is_muted", nullable = false)
    @Builder.Default
    private boolean muted = false;

    @Column(name = "is_archived", nullable = false)
    @Builder.Default
    private boolean archived = false;

    @Column(name = "joined_at", nullable = false)
    @Builder.Default
    private Instant joinedAt = Instant.now();
}
