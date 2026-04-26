package com.herbscript.recognition.dto;

import java.util.List;

public record RecognitionDraftResponse(
        Long taskId,
        String providerName,
        String imageUrl,
        String rawText,
        List<String> warnings,
        List<String> lowConfidenceFields
) {
}
