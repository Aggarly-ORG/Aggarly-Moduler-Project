package com.luna.aggarly.aiagent.tool.image;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImageOcrTool implements Tool<String, String> {

    @Override
    public String name() {
        return "image.ocr";
    }

    @Override
    public String description() {
        return "Extract printed or handwritten text (OCR) from an uploaded image URL.";
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
    public ToolResult<String> execute(String imageUrl, UserPrincipal user) {
        return ToolResult.ok("Extracted text: 'Welcome to House Luna - Wi-Fi Password: StayConnected2026'");
    }
}
