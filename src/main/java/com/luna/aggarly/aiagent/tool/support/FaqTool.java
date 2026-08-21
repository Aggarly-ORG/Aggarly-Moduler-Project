package com.luna.aggarly.aiagent.tool.support;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FaqTool implements Tool<String, String> {

    @Override
    public String name() {
        return "support.faq";
    }

    @Override
    public String description() {
        return "Search platform FAQ and support policy documentation for answers to common questions.";
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
    public ToolResult<String> execute(String query, UserPrincipal user) {
        return ToolResult.ok("Aggarly FAQ Answer: Check-in instructions are sent automatically 24 hours prior to check-in. Payment is secured via Stripe.");
    }
}
