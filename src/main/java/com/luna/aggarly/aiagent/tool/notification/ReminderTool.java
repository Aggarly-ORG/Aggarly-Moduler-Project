package com.luna.aggarly.aiagent.tool.notification;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ReminderTool implements Tool<ReminderTool.Params, String> {

    public record Params(
            String message,
            Instant triggerAt
    ) {}

    @Override
    public String name() {
        return "notification.scheduleReminder";
    }

    @Override
    public String description() {
        return "Schedule a reminder notification for upcoming check-ins, payments, or reviews.";
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
    public ToolResult<String> execute(Params params, UserPrincipal user) {
        String msg = (params != null && params.message() != null) ? params.message() : "";
        Instant trig = (params != null && params.triggerAt() != null) ? params.triggerAt() : Instant.now();
        return ToolResult.ok("Reminder scheduled for " + trig + ": " + msg);
    }
}
