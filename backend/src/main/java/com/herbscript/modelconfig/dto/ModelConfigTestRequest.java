package com.herbscript.modelconfig.dto;

public record ModelConfigTestRequest(
        Long profileId,
        String provider,
        String doubaoBaseUrl,
        String doubaoModel,
        String doubaoChatPath,
        String doubaoApiKey,
        Boolean fallbackToMockOnError
) {
}
