package com.herbscript.agent.dto;

public record AgentNoteResponse(
        Long id,
        Long sessionId,
        String anchorType,
        Long anchorId,
        String noteType,
        String title,
        String content,
        String answerConfidence,
        String remainingUncertaintiesJson,
        boolean pinned,
        String createdAt
) {
}
