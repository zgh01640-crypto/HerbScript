package com.herbscript.modelconfig.dto;

import jakarta.validation.constraints.NotNull;

public record ModelConfigActivateRequest(
        @NotNull(message = "档案 ID 不能为空")
        Long profileId
) {
}
