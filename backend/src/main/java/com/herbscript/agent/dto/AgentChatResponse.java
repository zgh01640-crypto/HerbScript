package com.herbscript.agent.dto;

import java.util.List;

public record AgentChatResponse(
        Long sessionId,
        AgentMessageResponse message,
        AgentStructuredResponse structured,
        List<AgentToolCallResponse> toolCalls
) {
}
