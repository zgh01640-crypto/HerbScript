package com.herbscript.agent.skill;

import com.herbscript.agent.skill.dto.SkillExecutionRequest;
import com.herbscript.agent.skill.dto.SkillExecutionResult;

public interface AgentSkill {

    String name();

    String description();

    SkillExecutionResult execute(SkillExecutionRequest request);
}
