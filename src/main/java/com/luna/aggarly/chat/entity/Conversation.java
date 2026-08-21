package com.luna.aggarly.chat.entity;

import com.luna.aggarly.chat.entity.enums.ConversationType;
import com.luna.aggarly.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("is_deleted=false")
@SQLDelete(sql = "UPDATE conversations SET is_deleted = true WHERE id = ?")
public class Conversation extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    @Builder.Default
    private ConversationType type = ConversationType.DIRECT;

    @Column(name = "property_id")
    private UUID propertyId;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "ai_conversation_id")
    private UUID aiConversationId;

    @Column(name = "title", length = 150)
    private String title;

    @Column(name = "name", length = 255)
    private String name;

    public String getName() {
        return name != null && !name.isBlank() ? name : title;
    }

    public String getTitle() {
        return title != null && !title.isBlank() ? title : name;
    }

    public void setTitle(String title) {
        this.title = title;
        if (this.name == null || this.name.isBlank()) {
            this.name = title;
        }
    }

    public void setName(String name) {
        this.name = name;
        if (this.title == null || this.title.isBlank()) {
            this.title = name;
        }
    }

    @Column(name = "last_message_at", nullable = false)
    @Builder.Default
    private Instant lastMessageAt = Instant.now();

    @Column(name = "last_message_preview", length = 255)
    private String lastMessagePreview;

    @OneToMany(mappedBy = "conversation", fetch = FetchType.LAZY)
    @Builder.Default
    private List<ConversationParticipant> participants = new ArrayList<>();

    @OneToMany(mappedBy = "conversation", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Message> messages = new ArrayList<>();
}
