package com.luna.aggarly.aiagent.engine;

import com.luna.aggarly.aiagent.agent.Agent;
import com.luna.aggarly.aiagent.engine.enums.IntentCategory;
import com.luna.aggarly.aiagent.engine.records.ClassifiedIntent;
import com.luna.aggarly.aiagent.engine.records.ConversationContext;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRouter {

    private final List<Agent> agents;
    private final PlanningEngine planningEngine;

    public AgentResponse route(ClassifiedIntent intent, ConversationContext context, UserPrincipal user) {
        log.info("Routing intent category={} for user={}", intent.category(), user != null ? user.getUserId() : "anonymous");

        if (intent.category() == IntentCategory.MULTI_STEP_COMPLEX) {
            return planningEngine.executePlan(intent, context, user);
        }

        return agents.stream()
                .filter(agent -> agent.supports(intent.category()))
                .findFirst()
                .map(agent -> agent.handle(intent, context, user))
                .orElseGet(() -> AgentResponse.fallback("I'm not sure how to help with that request right now."));
    }
}
