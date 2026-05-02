package com.herbscript.agent.skill.dto;

import com.herbscript.agent.context.AgentContext;

public record SkillExecutionRequest(
        AgentContext context,
        String userQuestion
) {
}
