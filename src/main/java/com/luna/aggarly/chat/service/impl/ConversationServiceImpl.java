package com.luna.aggarly.chat.service.impl;

import com.luna.aggarly.chat.dto.request.CreateConversationRequest;
import com.luna.aggarly.chat.dto.response.ConversationResponse;
import com.luna.aggarly.chat.dto.response.ConversationSummaryResponse;
import com.luna.aggarly.chat.dto.response.MessageResponse;
import com.luna.aggarly.chat.entity.Conversation;
import com.luna.aggarly.chat.entity.ConversationParticipant;
import com.luna.aggarly.chat.entity.enums.ConversationType;
import com.luna.aggarly.chat.entity.enums.MessageType;
import com.luna.aggarly.chat.entity.enums.ParticipantRole;
import com.luna.aggarly.chat.exceptions.ConversationNotFoundException;
import com.luna.aggarly.chat.exceptions.UnauthorizedChatAccessException;
import com.luna.aggarly.chat.mapper.ChatMapper;
import com.luna.aggarly.chat.repository.ConversationParticipantRepository;
import com.luna.aggarly.chat.repository.ConversationRepository;
import com.luna.aggarly.chat.repository.MessageRepository;
import com.luna.aggarly.chat.service.ChatAiBridgeService;
import com.luna.aggarly.chat.service.ConversationService;
import com.luna.aggarly.chat.service.MessageService;
import com.luna.aggarly.user.repository.UserRepository;
import com.luna.aggarly.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ChatMapper chatMapper;

    @Override
    @Transactional
    public ConversationResponse createConversation(CreateConversationRequest request, UUID creatorId) {
        log.info("Creating conversation of type {} by user {}", request.type(), creatorId);

        // If direct or booking inquiry, check if already exists between these 2 users
        if ((request.type() == ConversationType.DIRECT || request.type() == ConversationType.BOOKING_INQUIRY)
                && request.recipientId() != null) {
            Optional<Conversation> existing = conversationRepository.findDirectConversationBetween(
                    creatorId, request.recipientId(), request.type());
            if (existing.isPresent()) {
                return chatMapper.toResponse(existing.get());
            }
        }

        String title = request.title();
        if (title == null || title.isBlank()) {
            if (request.type() == ConversationType.AI_CONCIERGE) {
                title = "Trip Planning Inquiry";
            } else if (request.type() == ConversationType.BOOKING_INQUIRY) {
                title = "Booking Inquiry";
            } else {
                title = "Direct Message";
            }
        }

        Conversation conversation = Conversation.builder()
                .type(request.type())
                .propertyId(request.propertyId())
                .bookingId(request.bookingId())
                .title(title)
                .lastMessageAt(Instant.now())
                .lastMessagePreview(request.initialMessage() != null ? request.initialMessage() : "Conversation started")
                .build();

        Conversation saved = conversationRepository.save(conversation);

        // Add creator as participant
        ConversationParticipant creator = ConversationParticipant.builder()
                .conversation(saved)
                .userId(creatorId)
                .role(ParticipantRole.GUEST)
                .unreadCount(0)
                .lastReadAt(Instant.now())
                .build();
        participantRepository.save(creator);

        // Add recipient if provided
        if (request.recipientId() != null) {
            ConversationParticipant recipient = ConversationParticipant.builder()
                    .conversation(saved)
                    .userId(request.recipientId())
                    .role(ParticipantRole.HOST)
                    .unreadCount(request.initialMessage() != null ? 1 : 0)
                    .build();
            participantRepository.save(recipient);
        }

        // If AI Concierge, add system AI Bot as participant
        if (request.type() == ConversationType.AI_CONCIERGE) {
            ConversationParticipant aiBot = ConversationParticipant.builder()
                    .conversation(saved)
                    .userId(ChatAiBridgeService.AI_BOT_SYSTEM_ID)
                    .role(ParticipantRole.AI_BOT)
                    .unreadCount(0)
                    .build();
            participantRepository.save(aiBot);
        }

        return chatMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ConversationResponse getOrCreateAiConciergeConversation(UUID userId) {
        Optional<Conversation> existing = conversationRepository.findAiConciergeConversation(userId);
        if (existing.isPresent()) {
            return chatMapper.toResponse(existing.get());
        }

        CreateConversationRequest request = new CreateConversationRequest(
                ConversationType.AI_CONCIERGE,
                null,
                null,
                null,
                "Aggarly AI Concierge",
                "Hello! How can I help you plan your trip today?"
        );
        return createConversation(request, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ConversationSummaryResponse> getUserConversations(UUID userId, Pageable pageable) {
        return conversationRepository.findUserActiveConversations(userId, pageable)
                .map(conv -> {
                    ConversationSummaryResponse summary = chatMapper.toSummaryResponse(conv);
                    // Populate unread count for current user
                    int unread = conv.getParticipants().stream()
                            .filter(p -> p.getUserId().equals(userId))
                            .mapToInt(ConversationParticipant::getUnreadCount)
                            .findFirst()
                            .orElse(0);

                    String effectiveTitle = summary.title();
                    if (conv.getType() == ConversationType.DIRECT) {
                        UUID otherUserId = conv.getParticipants().stream()
                                .map(ConversationParticipant::getUserId)
                                .filter(id -> !id.equals(userId))
                                .findFirst()
                                .orElse(null);
                        if (otherUserId != null) {
                            Optional<User> other = userRepository.findById(otherUserId);
                            if (other.isPresent()) {
                                User u = other.get();
                                String name = u.getDisplayName();
                                if (name == null || name.isBlank()) {
                                    name = (u.getFirstName() != null ? u.getFirstName() + (u.getLastName() != null ? " " + u.getLastName() : "") : u.getUsername());
                                }
                                if (name != null && !name.isBlank()) {
                                    effectiveTitle = name;
                                }
                            }
                        }
                    }

                    return new ConversationSummaryResponse(
                            summary.id(),
                            summary.type(),
                            summary.propertyId(),
                            summary.bookingId(),
                            effectiveTitle,
                            summary.lastMessageAt(),
                            summary.lastMessagePreview(),
                            unread,
                            summary.participants()
                    );
                });
    }

    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    private boolean isZeroOrNull(UUID id) {
        return id == null || id.equals(ZERO_UUID);
    }

    @Override
    @Transactional
    public ConversationResponse getConversationById(UUID conversationId, UUID currentUserId) {
        if (isZeroOrNull(conversationId)) {
            return getOrCreateAiConciergeConversation(currentUserId);
        }

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseGet(() -> {
                    log.info("Conversation {} not found, creating new AI Concierge thread for user {}", conversationId, currentUserId);
                    ConversationResponse created = getOrCreateAiConciergeConversation(currentUserId);
                    return conversationRepository.findById(created.id())
                            .orElseThrow(() -> new ConversationNotFoundException(conversationId));
                });

        boolean isParticipant = conversation.getParticipants().stream()
                .anyMatch(p -> p.getUserId().equals(currentUserId));

        if (!isParticipant) {
            throw new UnauthorizedChatAccessException("You are not a participant in this conversation");
        }

        return chatMapper.toResponse(conversation);
    }

    @Override
    @Transactional
    public List<MessageResponse> getRecentMessages(UUID conversationId, int limit, UUID currentUserId) {
        if (isZeroOrNull(conversationId)) {
            ConversationResponse convResp = getOrCreateAiConciergeConversation(currentUserId);
            conversationId = convResp.id();
        }

        final UUID finalConvId = conversationId;
        Conversation conversation = conversationRepository.findById(finalConvId)
                .orElseGet(() -> {
                    ConversationResponse created = getOrCreateAiConciergeConversation(currentUserId);
                    return conversationRepository.findById(created.id())
                            .orElseThrow(() -> new ConversationNotFoundException(finalConvId));
                });

        boolean isParticipant = conversation.getParticipants().stream()
                .anyMatch(p -> p.getUserId().equals(currentUserId));

        if (!isParticipant) {
            throw new UnauthorizedChatAccessException("You are not a participant in this conversation");
        }

        int max = Math.min(Math.max(1, limit), 100);
        return chatMapper.toMessageResponseList(messageRepository.findRecentMessages(conversation.getId(), PageRequest.of(0, max)));
    }

    @Override
    @Transactional
    public void markConversationAsRead(UUID conversationId, UUID userId, UUID lastReadMessageId) {
        participantRepository.findByConversationIdAndUserId(conversationId, userId).ifPresent(p -> {
            p.setUnreadCount(0);
            p.setLastReadAt(Instant.now());
            participantRepository.save(p);
            log.debug("Marked conversation {} read for user {}", conversationId, userId);
        });
    }

    @Override
    @Transactional
    public void muteConversation(UUID conversationId, UUID userId, boolean muted) {
        participantRepository.findByConversationIdAndUserId(conversationId, userId).ifPresent(p -> {
            p.setMuted(muted);
            participantRepository.save(p);
        });
    }

    @Override
    @Transactional
    public void archiveConversation(UUID conversationId, UUID userId, boolean archived) {
        participantRepository.findByConversationIdAndUserId(conversationId, userId).ifPresent(p -> {
            p.setArchived(archived);
            participantRepository.save(p);
        });
    }
}
