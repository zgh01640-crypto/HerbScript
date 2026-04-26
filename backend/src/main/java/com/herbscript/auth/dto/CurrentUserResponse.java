package com.herbscript.auth.dto;

import java.util.List;

public record CurrentUserResponse(
        Long id,
        String username,
        String realName,
        List<String> roles
) {
}
