package com.luna.aggarly.aiagent.tool.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.engine.records.ImageAnalysisResult;
import com.luna.aggarly.aiagent.engine.VisionClient;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImageCaptionTool implements Tool<String, ImageAnalysisResult> {

    private final VisionClient visionClient;

    @Override
    public String name() {
        return "image.caption";
    }

    @Override
    public String description() {
        return "Generate AI-driven descriptive caption, ALT text, detected objects, and visual style tags for an image.";
    }

    @Override
    public Class<String> parameterType() {
        return String.class;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ToolResult<ImageAnalysisResult> execute(String imageUrl, UserPrincipal user) {
        ImageAnalysisResult result = visionClient.analyzeImage(imageUrl);
        return ToolResult.ok(result);
    }
}
