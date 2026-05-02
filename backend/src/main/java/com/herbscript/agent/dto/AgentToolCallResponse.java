package com.herbscript.agent.dto;

public record AgentToolCallResponse(
        Long id,
        Long messageId,
        String toolName,
        String toolLabel,
        String status,
        Integer latencyMs,
        Object inputJson,
        Object outputJson,
        String createdAt
) {
}
