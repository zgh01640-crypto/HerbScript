package com.herbscript.agent.tool.dto;

import java.util.Map;

public record ToolExecutionRequest(
        Map<String, Object> arguments
) {
}
