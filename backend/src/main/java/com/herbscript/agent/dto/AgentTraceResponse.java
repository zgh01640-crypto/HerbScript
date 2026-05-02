package com.herbscript.agent.dto;

public record AgentTraceResponse(
        Long id,
        Long messageId,
        String modelName,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Integer latencyMs,
        String tracePayload,
        String createdAt
) {
}
