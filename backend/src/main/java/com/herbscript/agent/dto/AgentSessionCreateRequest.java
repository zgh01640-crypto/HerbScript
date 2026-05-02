package com.herbscript.agent.dto;

import jakarta.validation.constraints.NotBlank;

public record AgentSessionCreateRequest(
        @NotBlank(message = "锚点类型不能为空")
        String anchorType,
        Long anchorId,
        @NotBlank(message = "会话标题不能为空")
        String title
) {
}
