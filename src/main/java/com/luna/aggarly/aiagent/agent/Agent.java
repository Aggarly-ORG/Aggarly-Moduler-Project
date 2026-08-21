package com.luna.aggarly.aiagent.agent;

import com.luna.aggarly.aiagent.engine.AgentResponse;
import com.luna.aggarly.aiagent.engine.records.ClassifiedIntent;
import com.luna.aggarly.aiagent.engine.records.ConversationContext;
import com.luna.aggarly.aiagent.engine.enums.IntentCategory;
import com.luna.aggarly.user.security.UserPrincipal;

import java.util.List;

public interface Agent {

    String name();

    String description();

    boolean supports(IntentCategory category);

    AgentResponse handle(ClassifiedIntent intent, ConversationContext context, UserPrincipal user);

    List<String> supportedTools();
}
