package com.herbscript.agent.tool;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ToolRegistry {

    private final Map<String, AgentTool> tools;

    public ToolRegistry(List<AgentTool> tools) {
        this.tools = tools.stream().collect(Collectors.toMap(AgentTool::name, Function.identity()));
    }

    public AgentTool get(String name) {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未注册的工具: " + name);
        }
        return tool;
    }
}
