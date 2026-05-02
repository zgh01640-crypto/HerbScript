package com.herbscript.agent.model;

import com.herbscript.agent.dto.AgentStructuredResponse;

public record AgentModelResult(
        String content,
        AgentStructuredResponse structured,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Integer latencyMs,
        boolean fallbackUsed
) {
}
