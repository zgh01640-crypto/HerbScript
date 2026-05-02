package com.herbscript.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AgentChatRequest(
        @NotNull(message = "会话 ID 不能为空")
        Long sessionId,
        @NotBlank(message = "锚点类型不能为空")
        String anchorType,
        Long anchorId,
        @NotBlank(message = "消息不能为空")
        String message
) {
}
