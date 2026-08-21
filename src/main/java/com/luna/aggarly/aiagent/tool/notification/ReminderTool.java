package com.luna.aggarly.aiagent.tool.notification;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ReminderTool implements Tool<ReminderTool.ReminderRequest, String> {

    public record ReminderRequest(
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
    public Class<ReminderRequest> parameterType() {
        return ReminderRequest.class;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ToolResult<String> execute(ReminderRequest params, UserPrincipal user) {
        return ToolResult.ok("Reminder scheduled for " + params.triggerAt() + ": " + params.message());
    }
}
