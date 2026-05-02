package com.herbscript.agent.dto;

public record AgentSessionSummaryResponse(
        Long id,
        String anchorType,
        Long anchorId,
        String title,
        String sessionStatus,
        String lastMessageAt,
        String lastAssistantSummary,
        String createdAt
) {
}
