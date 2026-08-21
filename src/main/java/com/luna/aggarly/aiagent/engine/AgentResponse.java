package com.luna.aggarly.aiagent.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResponse {

    private String text;
    private List<String> toolCalls;
    private String updatedSearchFilters;
    private boolean requiresConfirmation;
    private String confirmationToken;
    private String pendingToolName;
    private String metadataJson;

    public String getText() { return text; }
    public List<String> getToolCalls() { return toolCalls; }
    public String getUpdatedSearchFilters() { return updatedSearchFilters; }
    public boolean isRequiresConfirmation() { return requiresConfirmation; }
    public String getConfirmationToken() { return confirmationToken; }
    public String getPendingToolName() { return pendingToolName; }
    public String getMetadataJson() { return metadataJson; }

    public static AgentResponse fallback(String message) {
        return builder()
                .text(message)
                .toolCalls(List.of())
                .build();
    }

    public static AgentResponse error(String errorMessage) {
        return builder()
                .text("I encountered an error processing your request: " + errorMessage)
                .toolCalls(List.of())
                .build();
    }

    public static AgentResponse partial(String partialMessage) {
        return builder()
                .text(partialMessage)
                .toolCalls(List.of())
                .build();
    }

    public static AgentResponse awaitingConfirmation(
            String message,
            String confirmationToken,
            String pendingToolName,
            List<String> executedTools
    ) {
        return builder()
                .text(message)
                .toolCalls(executedTools)
                .requiresConfirmation(true)
                .confirmationToken(confirmationToken)
                .pendingToolName(pendingToolName)
                .build();
    }

    public String toolCallsAsJson() {
        if (toolCalls == null || toolCalls.isEmpty()) return "[]";
        return "[\"" + String.join("\",\"", toolCalls) + "\"]";
    }

    public static AgentResponseBuilder builder() {
        return new AgentResponseBuilder();
    }

    public static class AgentResponseBuilder {
        private String text;
        private List<String> toolCalls = new ArrayList<>();
        private String updatedSearchFilters;
        private boolean requiresConfirmation;
        private String confirmationToken;
        private String pendingToolName;
        private String metadataJson;

        public AgentResponseBuilder text(String text) { this.text = text; return this; }
        public AgentResponseBuilder toolCalls(List<String> toolCalls) { this.toolCalls = toolCalls; return this; }
        public AgentResponseBuilder updatedSearchFilters(String filters) { this.updatedSearchFilters = filters; return this; }
        public AgentResponseBuilder requiresConfirmation(boolean req) { this.requiresConfirmation = req; return this; }
        public AgentResponseBuilder confirmationToken(String token) { this.confirmationToken = token; return this; }
        public AgentResponseBuilder pendingToolName(String name) { this.pendingToolName = name; return this; }
        public AgentResponseBuilder metadataJson(String json) { this.metadataJson = json; return this; }

        public AgentResponse build() {
            return new AgentResponse(text, toolCalls, updatedSearchFilters, requiresConfirmation, confirmationToken, pendingToolName, metadataJson);
        }
    }
}
