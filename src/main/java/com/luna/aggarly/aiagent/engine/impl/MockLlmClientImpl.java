package com.luna.aggarly.aiagent.engine.impl;

import com.luna.aggarly.aiagent.engine.LlmClient;
import com.luna.aggarly.aiagent.engine.records.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Mock LLM client used in tests and local development (aggarly.ai.provider=mock).
 *
 * chat()         — keyword-based intent detection
 * chatWithTools  — simulates tool call detection from the last user message
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "aggarly.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockLlmClientImpl implements LlmClient {

    @Override
    public String chat(List<ChatMessage> messages, String modelOverride) {
        log.info("MockLlmClient.chat: messages={}, modelOverride={}", messages != null ? messages.size() : 0, modelOverride);

        if (messages == null || messages.isEmpty()) return "PROPERTY_SEARCH";

        // Use the last user message for intent detection
        String text = messages.stream()
                .filter(m -> "user".equalsIgnoreCase(m.role()))
                .reduce((a, b) -> b)
                .map(ChatMessage::content)
                .orElse("")
                .toLowerCase();

        if (text.contains("book") || text.contains("cancel") || text.contains("reserve"))
            return "BOOKING_ACTION";
        if (text.contains("compare") || text.contains("versus"))
            return "PROPERTY_COMPARISON";
        if (text.contains("available") || text.contains("dates"))
            return "AVAILABILITY_QUESTION";
        if (text.contains("plan") || text.contains("trip") || text.contains("itinerary"))
            return "MULTI_STEP_COMPLEX";

        return "PROPERTY_SEARCH";
    }

    @Override
    public LlmToolCallResponse chatWithTools(List<ChatMessage> messages, List<ToolDefinition> tools, String modelOverride) {
        log.info("MockLlmClient.chatWithTools: messages={}, tools={}",
                messages != null ? messages.size() : 0,
                tools != null ? tools.size() : 0);

        if (messages == null || messages.isEmpty()) {
            return new LlmToolCallResponse("I'm ready to help with your booking.", List.of());
        }

        ChatMessage last = messages.get(messages.size() - 1);

        // If the last message is a tool result → produce a final text answer
        if ("tool".equalsIgnoreCase(last.role())) {
            return new LlmToolCallResponse(
                    "I've processed the tool result: " + last.content(),
                    List.of()
            );
        }

        String text = last.content().toLowerCase();

        // Simulate tool calls based on keywords
        if (text.contains("cancel")) {
            return new LlmToolCallResponse(null, List.of(
                    new LlmToolCall("booking.cancellationQuote",
                            Map.of("bookingId", "3fa85f64-5717-4562-b3fc-2c963f66afa6"))
            ));
        }
        if (text.contains("price") || text.contains("cost") || text.contains("quote")) {
            return new LlmToolCallResponse(null, List.of(
                    new LlmToolCall("booking.priceExplanation",
                            Map.of("propertyId", "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                                   "checkIn", "2025-09-10",
                                   "checkOut", "2025-09-15",
                                   "guests", 2))
            ));
        }
        if (text.contains("available") || text.contains("availability")) {
            return new LlmToolCallResponse(null, List.of(
                    new LlmToolCall("property.availability",
                            Map.of("propertyId", "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                                   "checkIn", "2025-09-10",
                                   "checkOut", "2025-09-15"))
            ));
        }
        if (text.contains("book") || text.contains("reserve")) {
            return new LlmToolCallResponse(null, List.of(
                    new LlmToolCall("booking.create",
                            Map.of("propertyId", "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                                   "checkIn", "2025-09-10",
                                   "checkOut", "2025-09-15",
                                   "guests", 2))
            ));
        }
        if (text.contains("check-in") || text.contains("check in") || text.contains("wifi") || text.contains("code")) {
            return new LlmToolCallResponse(null, List.of(
                    new LlmToolCall("booking.checkInInstructions",
                            Map.of("bookingId", "3fa85f64-5717-4562-b3fc-2c963f66afa6"))
            ));
        }

        // No tool needed — plain answer
        return new LlmToolCallResponse(
                "I'm ready to help with your booking. Please provide more details.",
                List.of()
        );
    }
}
