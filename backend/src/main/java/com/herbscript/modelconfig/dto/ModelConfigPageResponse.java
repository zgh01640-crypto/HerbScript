package com.herbscript.modelconfig.dto;

import java.util.List;

public record ModelConfigPageResponse(
        Long activeProfileId,
        List<ModelConfigProfileResponse> profiles
) {
}
