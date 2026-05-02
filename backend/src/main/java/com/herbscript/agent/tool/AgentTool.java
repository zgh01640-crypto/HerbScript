package com.herbscript.agent.tool;

import com.herbscript.agent.tool.dto.ToolExecutionRequest;
import com.herbscript.agent.tool.dto.ToolExecutionResult;

public interface AgentTool {

    String name();

    String description();

    ToolExecutionResult execute(ToolExecutionRequest request);
}
