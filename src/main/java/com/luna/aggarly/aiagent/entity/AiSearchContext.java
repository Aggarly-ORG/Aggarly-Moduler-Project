package com.luna.aggarly.aiagent.entity;

import com.luna.aggarly.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "ai_search_contexts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiSearchContext extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID conversationId;

    @Column(columnDefinition = "TEXT")
    private String filtersJson;

    public String getFiltersJson() { return filtersJson; }
    public UUID getConversationId() { return conversationId; }
}
