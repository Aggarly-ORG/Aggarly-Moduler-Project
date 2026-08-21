package com.luna.aggarly.chat.controller;

import com.luna.aggarly.chat.dto.request.CreateConversationRequest;
import com.luna.aggarly.chat.dto.request.SendRestMessageRequest;
import com.luna.aggarly.chat.dto.response.ConversationResponse;
import com.luna.aggarly.chat.dto.response.ConversationSummaryResponse;
import com.luna.aggarly.chat.dto.response.MessageResponse;
import com.luna.aggarly.chat.entity.enums.MessageType;
import com.luna.aggarly.chat.service.ConversationService;
import com.luna.aggarly.chat.service.MessageService;
import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.user.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controller providing REST endpoints for chat threads, messaging inbox, and fallback communication.
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Real-Time & REST Messaging APIs")
public class ChatRestController {

    private final ConversationService conversationService;
    private final MessageService messageService;

    @PostMapping("/conversations")
    @Operation(summary = "Start or retrieve a conversation thread", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<ConversationResponse>> createConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateConversationRequest request) {
        ConversationResponse response = conversationService.createConversation(request, principal.getUserId());
        return ApiResponse.created(response, "Conversation thread created successfully").toResponseEntity();
    }

    @PostMapping("/conversations/ai-concierge")
    @Operation(summary = "Open a conversation with Aggarly AI Concierge", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<ConversationResponse>> openAiConcierge(
            @AuthenticationPrincipal UserPrincipal principal) {
        ConversationResponse response = conversationService.getOrCreateAiConciergeConversation(principal.getUserId());
        return ApiResponse.ok(response, "AI concierge thread retrieved successfully").toResponseEntity();
    }

    @GetMapping("/conversations")
    @Operation(summary = "List user conversation inbox with unread counts", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<ConversationSummaryResponse>>> getUserInbox(
            @AuthenticationPrincipal UserPrincipal principal,
            Pageable pageable) {
        Page<ConversationSummaryResponse> response = conversationService.getUserConversations(principal.getUserId(), pageable);
        return ApiResponse.paged(response, "User inbox conversations retrieved").toResponseEntity();
    }

    @GetMapping("/conversations/{id}")
    @Operation(summary = "Get conversation details by ID", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<ConversationResponse>> getConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        ConversationResponse response = conversationService.getConversationById(id, principal.getUserId());
        return ApiResponse.ok(response, "Conversation details retrieved successfully").toResponseEntity();
    }

    @GetMapping("/conversations/{id}/messages")
    @Operation(summary = "Get message history for a conversation", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<MessageResponse>>> getMessages(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            Pageable pageable) {
        Page<MessageResponse> response = messageService.getConversationMessages(id, principal.getUserId(), pageable);
        return ApiResponse.paged(response, "Conversation messages retrieved").toResponseEntity();
    }

    @PostMapping("/conversations/{id}/messages")
    @Operation(summary = "Send a message via REST fallback", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody SendRestMessageRequest request) {
        MessageResponse response = messageService.sendMessage(
                id,
                principal.getUserId(),
                request.content(),
                request.messageType() != null ? request.messageType() : MessageType.TEXT,
                request.metadataJson()
        );
        return ApiResponse.created(response, "Message sent successfully").toResponseEntity();
    }

    @PatchMapping("/conversations/{id}/read")
    @Operation(summary = "Mark conversation as read", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> markRead(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam(required = false) UUID lastReadMessageId) {
        conversationService.markConversationAsRead(id, principal.getUserId(), lastReadMessageId);
        return ApiResponse.<Void>empty("Conversation marked as read").toResponseEntity();
    }

    @PatchMapping("/conversations/{id}/mute")
    @Operation(summary = "Mute or unmute conversation", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> muteConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam boolean muted) {
        conversationService.muteConversation(id, principal.getUserId(), muted);
        return ApiResponse.<Void>empty("Conversation mute setting updated").toResponseEntity();
    }

    @PatchMapping("/conversations/{id}/archive")
    @Operation(summary = "Archive or unarchive conversation", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> archiveConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam boolean archived) {
        conversationService.archiveConversation(id, principal.getUserId(), archived);
        return ApiResponse.<Void>empty("Conversation archive setting updated").toResponseEntity();
    }
}
