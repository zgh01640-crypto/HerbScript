package com.herbscript.agent.tool.dto;

public record ToolExecutionResult(
        String toolName,
        String status,
        Object payload,
        String errorMessage,
        Integer latencyMs
) {
}
