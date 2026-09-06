package com.luna.aggarly.aiagent.tool.vision;

import com.luna.aggarly.aiagent.engine.MemoryContextManager;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.common.security.SecurityUtils;
import com.luna.aggarly.user.security.UserPrincipal;
import com.luna.aggarly.vision.search.VisualPreferenceExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class VisionExtractVisualPreferencesTool implements Tool<VisionExtractVisualPreferencesTool.Params, VisualPreferenceExtractor.ExtractedPreferences> {

    private final VisualPreferenceExtractor preferenceExtractor;
    private final MemoryContextManager memoryContextManager;

    public record Params(
            String referenceObjectKey,
            boolean saveAsMemory
    ) {}

    @Override
    public String name() {
        return "vision.extractVisualPreferences";
    }

    @Override
    public String description() {
        return "Extract user aesthetic preferences and styles from a reference photo and optionally record them in user long-term memory for personalized recommendations.";
    }

    @Override
    public Class<Params> parameterType() {
        return Params.class;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ToolResult<VisualPreferenceExtractor.ExtractedPreferences> execute(Params params, UserPrincipal currentUser) {
        UUID userId = currentUser != null ? currentUser.getUserId() : SecurityUtils.getCurrentUserId();
        if (userId == null) {
            userId = UUID.randomUUID();
        }
        log.info("Executing tool vision.extractVisualPreferences for userId={}", userId);

        VisualPreferenceExtractor.ExtractedPreferences extracted = preferenceExtractor.extractPreferencesFromImage(
                params.referenceObjectKey(), userId
        );

        if (params.saveAsMemory()) {
            try {
                memoryContextManager.confirmLongTermMemory(
                        userId,
                        "VISUAL_PREFERENCE",
                        extracted.naturalLanguageSummary()
                );
                log.info("Persisted visual preference to long-term memory for userId={}", userId);
            } catch (Exception e) {
                log.warn("Failed to persist visual memory: {}", e.getMessage());
            }
        }

        return ToolResult.success(extracted);
    }
}
