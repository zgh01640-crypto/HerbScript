package com.herbscript.agent.dto;

public record AgentSessionResponse(
        Long id,
        String anchorType,
        Long anchorId,
        String title,
        String sessionStatus,
        String lastMessageAt,
        String createdAt
) {
}
