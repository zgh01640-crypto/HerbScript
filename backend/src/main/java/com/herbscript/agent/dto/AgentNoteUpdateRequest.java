package com.herbscript.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentNoteUpdateRequest(
        @NotBlank @Size(max = 255) String title
) {
}
