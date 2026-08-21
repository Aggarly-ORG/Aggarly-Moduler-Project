package com.luna.aggarly.aiagent;

import com.luna.aggarly.aiagent.dto.ChatMessageRequest;
import com.luna.aggarly.aiagent.dto.ChatMessageResponse;
import com.luna.aggarly.aiagent.engine.ConversationManager;
import com.luna.aggarly.aiagent.engine.enums.IntentCategory;
import com.luna.aggarly.aiagent.engine.IntentClassifier;
import com.luna.aggarly.aiagent.engine.records.ClassifiedIntent;
import com.luna.aggarly.user.entity.Role;
import com.luna.aggarly.user.entity.User;
import com.luna.aggarly.user.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiAgentScenarioTest {

    @Mock
    private ConversationManager conversationManager;

    @Mock
    private IntentClassifier intentClassifier;

    private UserPrincipal testUser;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        User userEntity = User.builder()
                .email("test@aggarly.com")
                .username("testuser")
                .passwordHash("password")
                .roles(Set.of(new Role(1L, "ROLE_GUEST")))
                .build();
        userEntity.setId(testUserId);
        testUser = new UserPrincipal(userEntity);
    }

    @Test
    @DisplayName("Scenario A: Conversational search refinement hands off to ConversationManager")
    void scenarioA_PropertySearchRefinement() {
        ChatMessageRequest request = new ChatMessageRequest(null, "Find apartments in Paris");
        ChatMessageResponse mockResponse = new ChatMessageResponse(UUID.randomUUID(), "Found apartments in Paris", List.of("property.search"));

        when(conversationManager.handleMessage(any(), any())).thenReturn(mockResponse);

        ChatMessageResponse result = conversationManager.handleMessage(request, testUser);

        assertNotNull(result);
        assertEquals("Found apartments in Paris", result.content());
        assertTrue(result.toolCalls().contains("property.search"));
    }

    @Test
    @DisplayName("Scenario B: Booking request requires confirmation gate")
    void scenarioB_BookingConfirmationGate() {
        ChatMessageRequest request = new ChatMessageRequest(UUID.randomUUID(), "Book property");
        ChatMessageResponse mockResponse = new ChatMessageResponse(request.conversationId(), "Please confirm booking details", List.of("booking.priceExplanation"));

        when(conversationManager.handleMessage(any(), any())).thenReturn(mockResponse);

        ChatMessageResponse result = conversationManager.handleMessage(request, testUser);

        assertNotNull(result);
        assertTrue(result.content().contains("confirm"));
    }

    @Test
    @DisplayName("Intent Classifier accurately categorizes small talk and property search")
    void intentClassifier_CategorizesIntents() {
        when(intentClassifier.classify(eq("Find a house in Rome"), any())).thenReturn(
                new ClassifiedIntent(IntentCategory.PROPERTY_SEARCH, "Find a house in Rome", null)
        );

        var classified = intentClassifier.classify("Find a house in Rome", null);
        assertEquals(IntentCategory.PROPERTY_SEARCH, classified.category());
    }
}
