package com.luna.aggarly.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_confirmed_actions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserConfirmedAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "confirmation_token", nullable = false, unique = true)
    private String confirmationToken;

    @Column(name = "tool_name", nullable = false, length = 100)
    private String toolName;

    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "CONFIRMED";

    @Column(name = "details_json", columnDefinition = "TEXT")
    private String detailsJson;

    @CreationTimestamp
    @Column(name = "confirmed_at", nullable = false, updatable = false)
    private Instant confirmedAt;
}
