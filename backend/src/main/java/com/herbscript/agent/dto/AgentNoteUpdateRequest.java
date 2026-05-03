package com.herbscript.agent.dto;

import jakarta.validation.constraints.Size;

public record AgentNoteUpdateRequest(
        @Size(max = 255) String title,
        String content
) {
}
