package com.luna.aggarly.aiagent.engine.records;

public record SupervisorDecision(
        String action,
        String agentName,
        String taskDescription,
        String finalSummary
) {
}
