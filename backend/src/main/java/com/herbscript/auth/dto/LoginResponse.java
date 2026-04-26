package com.herbscript.auth.dto;

public record LoginResponse(
        String token,
        CurrentUserResponse user
) {
}
