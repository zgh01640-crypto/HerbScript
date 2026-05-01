package com.herbscript.modelconfig.dto;

public record ModelConfigProfileResponse(
        Long id,
        String profileName,
        String provider,
        String doubaoBaseUrl,
        String doubaoModel,
        String doubaoChatPath,
        boolean fallbackToMockOnError,
        boolean apiKeyConfigured,
        String maskedApiKey,
        boolean active,
        boolean online,
        String updatedAt
) {
}
