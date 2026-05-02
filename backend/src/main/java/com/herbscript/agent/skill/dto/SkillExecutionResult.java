package com.herbscript.agent.skill.dto;

import com.herbscript.agent.dto.AgentStructuredResponse;
import java.util.List;

public record SkillExecutionResult(
        String skillName,
        String content,
        AgentStructuredResponse structured,
        List<String> usedTools
) {
}
