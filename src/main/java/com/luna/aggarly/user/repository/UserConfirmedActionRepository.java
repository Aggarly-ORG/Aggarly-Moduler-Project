package com.luna.aggarly.user.repository;

import com.luna.aggarly.user.entity.UserConfirmedAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserConfirmedActionRepository extends JpaRepository<UserConfirmedAction, UUID> {
    Optional<UserConfirmedAction> findByConfirmationToken(String confirmationToken);
    List<UserConfirmedAction> findByUserIdAndConversationId(UUID userId, UUID conversationId);
    List<UserConfirmedAction> findByUserId(UUID userId);
}
