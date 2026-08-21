package com.luna.aggarly.chat.mapper;

import com.luna.aggarly.chat.dto.response.ConversationParticipantResponse;
import com.luna.aggarly.chat.dto.response.ConversationResponse;
import com.luna.aggarly.chat.dto.response.ConversationSummaryResponse;
import com.luna.aggarly.chat.dto.response.MessageResponse;
import com.luna.aggarly.chat.entity.Conversation;
import com.luna.aggarly.chat.entity.ConversationParticipant;
import com.luna.aggarly.chat.entity.Message;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ChatMapper {

    @Mapping(target = "recentMessages", source = "messages")
    ConversationResponse toResponse(Conversation conversation);

    @Mapping(target = "unreadCount", ignore = true)
    ConversationSummaryResponse toSummaryResponse(Conversation conversation);

    List<ConversationSummaryResponse> toSummaryResponseList(List<Conversation> conversations);

    ConversationParticipantResponse toResponse(ConversationParticipant participant);

    List<ConversationParticipantResponse> toParticipantResponseList(List<ConversationParticipant> participants);

    @Mapping(target = "conversationId", source = "conversation.id")
    MessageResponse toResponse(Message message);

    List<MessageResponse> toMessageResponseList(List<Message> messages);
}
