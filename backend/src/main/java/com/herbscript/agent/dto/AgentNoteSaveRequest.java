package com.herbscript.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AgentNoteSaveRequest(
        Long sessionId,
        @NotBlank String anchorType,
        @NotNull Long anchorId,
        @NotBlank String noteType,
        @NotBlank @Size(max = 255) String title,
        @NotBlank String content,
        String answerConfidence,
        String remainingUncertaintiesJson
) {
}
