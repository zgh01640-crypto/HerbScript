package com.herbscript.agent.skill;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class SkillRegistry {

    private final Map<String, AgentSkill> skills;

    public SkillRegistry(List<AgentSkill> skills) {
        this.skills = skills.stream().collect(Collectors.toMap(AgentSkill::name, Function.identity()));
    }

    public AgentSkill get(String name) {
        AgentSkill skill = skills.get(name);
        if (skill == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未注册的技能: " + name);
        }
        return skill;
    }
}
