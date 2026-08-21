package com.luna.aggarly.aiagent.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.aiagent.engine.records.ChatMessage;
import com.luna.aggarly.aiagent.engine.records.ConversationContext;
import com.luna.aggarly.aiagent.entity.AiMessage;
import com.luna.aggarly.aiagent.entity.AiSearchContext;
import com.luna.aggarly.aiagent.entity.AiUserMemory;
import com.luna.aggarly.aiagent.entity.MessageRole;
import com.luna.aggarly.aiagent.repository.AiMessageRepository;
import com.luna.aggarly.aiagent.repository.AiSearchContextRepository;
import com.luna.aggarly.aiagent.repository.AiUserMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryContextManager {

    private final AiSearchContextRepository searchContextRepository;
    private final AiUserMemoryRepository userMemoryRepository;
    private final AiMessageRepository messageRepository;

    public ConversationContext loadContext(UUID conversationId, UUID userId) {
        log.debug("Loading context for conversationId={}, userId={}", conversationId, userId);
        AiSearchContext searchContext = conversationId != null ?
                searchContextRepository.findByConversationId(conversationId).orElse(null) : null;
        List<AiUserMemory> memories = userId != null ?
                userMemoryRepository.findByUserIdAndUserConfirmedTrue(userId) : List.of();

        List<ChatMessage> history = new ArrayList<>();
        if (conversationId != null) {
            List<AiMessage> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
            for (AiMessage msg : messages) {
                MessageRole role = msg.getRole();
                if (role == MessageRole.TOOL) {
                    history.add(ChatMessage.toolResponse(msg.getToolName(), msg.getContent()));
                } else {
                    String roleStr = role != null ? role.name().toLowerCase() : "user";
                    history.add(new ChatMessage(roleStr, msg.getContent(), null));
                }
            }
        }

        return new ConversationContext(searchContext, memories, history);
    }

    public void updateContext(UUID conversationId, AgentResponse response) {
        if (response.getUpdatedSearchFilters() != null && conversationId != null) {
            log.debug("Updating search context for conversationId={}", conversationId);
            AiSearchContext existing = searchContextRepository.findByConversationId(conversationId)
                    .orElse(AiSearchContext.builder().conversationId(conversationId).build());
            existing.setFiltersJson(response.getUpdatedSearchFilters());
            searchContextRepository.save(existing);
        }
    }

    public void confirmLongTermMemory(UUID userId, String key, String value) {
        log.info("Confirming long-term memory key={} for userId={}", key, userId);
        AiUserMemory memory = userMemoryRepository.findByUserIdAndMemoryKey(userId, key)
                .orElse(AiUserMemory.builder().userId(userId).memoryKey(key).build());
        memory.setMemoryValue(value);
        memory.setUserConfirmed(true);
        userMemoryRepository.save(memory);
    }

    public void forget(UUID userId, String key) {
        log.info("Forgetting memory key={} for userId={}", key, userId);
        userMemoryRepository.findByUserIdAndMemoryKey(userId, key)
                .ifPresent(userMemoryRepository::delete);
    }

    public List<AiUserMemory> listMemories(UUID userId) {
        return userMemoryRepository.findByUserIdAndUserConfirmedTrue(userId);
    }
}
