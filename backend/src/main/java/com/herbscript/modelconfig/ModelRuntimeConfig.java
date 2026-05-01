package com.herbscript.modelconfig;

public record ModelRuntimeConfig(
        String provider,
        String doubaoBaseUrl,
        String doubaoModel,
        String doubaoChatPath,
        String doubaoApiKey,
        boolean fallbackToMockOnError,
        String uploadDir
) {
}
