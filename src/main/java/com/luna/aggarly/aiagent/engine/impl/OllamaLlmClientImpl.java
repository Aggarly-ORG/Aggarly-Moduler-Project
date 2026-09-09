package com.luna.aggarly.aiagent.engine.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.aiagent.engine.LlmClient;
import com.luna.aggarly.aiagent.engine.records.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "aggarly.ai.provider", havingValue = "ollama")
public class OllamaLlmClientImpl implements LlmClient {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatClient chatClient;

    @Autowired
    public OllamaLlmClientImpl(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
        log.info("Initialized OllamaLlmClientImpl using Spring AI ChatClient");
    }

    @Override
    public String chat(List<ChatMessage> messages, String modelOverride) {
        log.info("Spring AI ChatClient chat: messagesCount={}, modelOverride={}", 
            messages != null ? messages.size() : 0, modelOverride);
        try {
            List<Message> springMessages = convertToSpringMessages(messages);
            
            Prompt prompt;
            if (modelOverride != null && !modelOverride.isBlank()) {
                prompt = new Prompt(springMessages, 
                    OllamaChatOptions.builder().model(modelOverride).build());
            } else {
                prompt = new Prompt(springMessages);
            }
            
            ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
            if (response != null
                    && response.getResult() != null
                    && response.getResult().getOutput() != null) {
                return response.getResult().getOutput().getText();
            }
            return "";
        } catch (Exception ex) {
            log.warn("Spring AI ChatClient chat() failed: {}", ex.getMessage());
            return "";
        }
    }

    @Override
    public LlmToolCallResponse chatWithTools(List<ChatMessage> messages, List<ToolDefinition> tools, String modelOverride) {
        log.info("Spring AI chatWithTools: messages={}, tools={}, modelOverride={}",
                messages != null ? messages.size() : 0,
                tools != null ? tools.size() : 0,
                modelOverride);

        List<ChatMessage> enriched = injectToolSchemas(messages, tools);

        String raw;
        try {
            raw = chat(enriched, modelOverride);
        } catch (Exception ex) {
            log.warn("LLM call failed in chatWithTools: {}", ex.getMessage());
            return new LlmToolCallResponse("I encountered an error. Please try again.", List.of());
        }

        if (raw == null || raw.isBlank()) {
            return new LlmToolCallResponse("", List.of());
        }

        return parseToolCallResponse(raw);
    }

    LlmToolCallResponse parseToolCallResponse(String raw) {
        String cleaned = raw.strip();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").strip();
        }

        try {
            JsonNode root = objectMapper.readTree(cleaned);

            if (root.has("tool_calls") && root.get("tool_calls").isArray()) {
                List<LlmToolCall> calls = new ArrayList<>();
                for (JsonNode callNode : root.get("tool_calls")) {
                    String name = callNode.path("name").asText(null);
                    JsonNode argsNode = callNode.path("arguments");

                    if (name == null || name.isBlank()) {
                        log.warn("Tool call missing 'name' field, skipping: {}", callNode);
                        continue;
                    }

                    Map<String, Object> args = new HashMap<>();
                    if (argsNode.isObject()) {
                        argsNode.fields().forEachRemaining(e ->
                                args.put(e.getKey(), jsonNodeToJavaValue(e.getValue()))
                        );
                    }

                    calls.add(new LlmToolCall(name, args));
                    log.debug("Parsed tool call: name={}, args={}", name, args);
                }

                if (!calls.isEmpty()) {
                    return new LlmToolCallResponse(null, calls);
                }
            }
        } catch (Exception ex) {
            log.debug("Response is not JSON or has no tool_calls — treating as plain text: {}", ex.getMessage());
        }

        return new LlmToolCallResponse(raw, List.of());
    }

    private List<ChatMessage> injectToolSchemas(List<ChatMessage> messages, List<ToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        }

        String toolBlock = buildToolSchemaBlock(tools);
        List<ChatMessage> enriched = new ArrayList<>();

        boolean systemFound = false;
        for (ChatMessage msg : messages) {
            if (!systemFound && "system".equalsIgnoreCase(msg.role())) {
                enriched.add(ChatMessage.system(msg.content() + "\n\n" + toolBlock));
                systemFound = true;
            } else {
                enriched.add(msg);
            }
        }

        if (!systemFound) {
            enriched.add(0, ChatMessage.system(toolBlock));
        }

        return enriched;
    }

    private String buildToolSchemaBlock(List<ToolDefinition> tools) {
        StringBuilder sb = new StringBuilder();
        sb.append("================================================================\n");
        sb.append("AVAILABLE TOOLS\n");
        sb.append("================================================================\n");
        sb.append("When you need to call a tool, respond ONLY with valid JSON:\n");
        sb.append("{\n  \"tool_calls\": [\n    {\n      \"name\": \"<tool_name>\",\n      \"arguments\": { ... }\n    }\n  ]\n}\n\n");
        sb.append("When you do NOT need a tool, respond with plain text only (no JSON).\n\n");
        sb.append("Tools:\n");

        for (ToolDefinition tool : tools) {
            sb.append("\n--- ").append(tool.name()).append(" ---\n");
            sb.append("Description: ").append(tool.description()).append("\n");
            if (tool.parametersSchema() != null && !tool.parametersSchema().isEmpty()) {
                try {
                    sb.append("Parameters:\n")
                      .append(objectMapper.writerWithDefaultPrettyPrinter()
                              .writeValueAsString(tool.parametersSchema()))
                      .append("\n");
                } catch (Exception ignored) {}
            }
            if (tool.responseSchema() != null && !tool.responseSchema().isEmpty()) {
                try {
                    sb.append("Response:\n")
                      .append(objectMapper.writerWithDefaultPrettyPrinter()
                              .writeValueAsString(tool.responseSchema()))
                      .append("\n");
                } catch (Exception ignored) {}
            }
        }

        return sb.toString();
    }

    private Object jsonNodeToJavaValue(JsonNode node) {
        if (node.isTextual())  return node.asText();
        if (node.isBoolean())  return node.asBoolean();
        if (node.isInt())      return node.asInt();
        if (node.isLong())     return node.asLong();
        if (node.isDouble())   return node.asDouble();
        if (node.isNull())     return null;
        try {
            return objectMapper.treeToValue(node, Object.class);
        } catch (Exception ex) {
            return node.toString();
        }
    }

    private List<Message> convertToSpringMessages(List<ChatMessage> messages) {
        List<Message> springMessages = new ArrayList<>();
        if (messages == null) return springMessages;
        for (ChatMessage msg : messages) {
            switch (msg.role().toLowerCase()) {
                case "system"    -> springMessages.add(new SystemMessage(msg.content()));
                case "assistant" -> springMessages.add(new AssistantMessage(msg.content()));
                case "tool"      -> springMessages.add(new UserMessage(
                        String.format("TOOL EXECUTION RESULT [%s]:\n%s",
                                msg.toolName() != null ? msg.toolName() : "unknown",
                                msg.content())
                ));
                default -> {
                    // Strip inline SCREENSHOT_URL: line from the text — the image will be sent as Media
                    String textContent = msg.content() == null ? "" : msg.content()
                            .replaceAll("(?m)^SCREENSHOT_URL:.*$\\n?", "").trim();

                    String imageUrl = msg.imageUrl();
                    if (imageUrl != null && !imageUrl.isBlank()) {
                        try {
                            byte[] imageBytes;
                            try (InputStream in = URI.create(imageUrl).toURL().openStream()) {
                                imageBytes = in.readAllBytes();
                            }
                            // Detect mime type from URL extension; default to PNG
                            String lower = imageUrl.toLowerCase();
                            String mimeStr = lower.contains(".jpg") || lower.contains(".jpeg")
                                    ? "image/jpeg"
                                    : lower.contains(".webp") ? "image/webp" : "image/png";
                            MimeType mimeType = MimeType.valueOf(mimeStr);
                            Media imageMedia = new Media(mimeType, new ByteArrayResource(imageBytes));
                            springMessages.add(UserMessage.builder().text(textContent).media(imageMedia).build());
                            log.debug("Attached screenshot as multimodal image part ({} bytes, {})", imageBytes.length, mimeStr);
                        } catch (Exception ex) {
                            log.warn("Failed to download screenshot for multimodal message, falling back to text-only: {}", ex.getMessage());
                            springMessages.add(new UserMessage(textContent));
                        }
                    } else {
                        springMessages.add(new UserMessage(textContent));
                    }
                }
            }
        }
        return springMessages;
    }
}

