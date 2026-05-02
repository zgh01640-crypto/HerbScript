package com.herbscript.agent.dto;

public record AgentMessageResponse(
        Long id,
        String role,
        String content,
        Object structuredPayload,
        String createdAt
) {
}
