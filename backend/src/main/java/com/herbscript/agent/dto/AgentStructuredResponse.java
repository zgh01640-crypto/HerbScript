package com.herbscript.agent.dto;

import java.util.List;

public record AgentStructuredResponse(
        String summary,
        List<String> observations,
        List<String> risks,
        List<String> suggestions
) {
}
