package com.herbscript.agent.context;

import com.herbscript.agent.dto.AgentContextSummaryResponse;
import java.util.List;
import java.util.Map;

public record AgentContext(
        String anchorType,
        Long anchorId,
        AgentContextSummaryResponse summary,
        Map<String, Object> payload,
        List<String> preferredQuestions
) {
}
