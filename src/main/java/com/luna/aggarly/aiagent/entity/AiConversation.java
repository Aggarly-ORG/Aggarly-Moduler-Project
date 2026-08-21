package com.luna.aggarly.aiagent.entity;

import com.luna.aggarly.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "ai_conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiConversation extends BaseEntity {

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private boolean active;

    private String title;

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
}
