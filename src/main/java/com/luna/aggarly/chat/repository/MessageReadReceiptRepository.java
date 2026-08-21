package com.luna.aggarly.chat.repository;

import com.luna.aggarly.chat.entity.MessageReadReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessageReadReceiptRepository extends JpaRepository<MessageReadReceipt, UUID> {

    Optional<MessageReadReceipt> findByMessageIdAndUserId(UUID messageId, UUID userId);

    List<MessageReadReceipt> findByMessageId(UUID messageId);
}
