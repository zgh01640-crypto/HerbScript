package com.herbscript.agent.dto;

import java.util.List;

public record AgentSessionDetailResponse(
        AgentSessionResponse session,
        AgentContextSummaryResponse contextSummary,
        List<AgentMessageResponse> messages
) {
}
