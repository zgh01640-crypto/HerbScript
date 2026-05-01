package com.herbscript.modelconfig.dto;

import jakarta.validation.constraints.NotBlank;

public record ModelConfigSaveRequest(
        Long profileId,
        @NotBlank(message = "档案名称不能为空")
        String profileName,
        @NotBlank(message = "提供方不能为空")
        String provider,
        @NotBlank(message = "Base URL 不能为空")
        String doubaoBaseUrl,
        @NotBlank(message = "模型名称不能为空")
        String doubaoModel,
        @NotBlank(message = "Chat Path 不能为空")
        String doubaoChatPath,
        Boolean fallbackToMockOnError,
        String doubaoApiKey,
        Boolean clearApiKey,
        Boolean activate
) {
}
