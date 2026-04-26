package com.herbscript.auth;

import com.herbscript.auth.dto.CurrentUserResponse;
import com.herbscript.auth.dto.LoginRequest;
import com.herbscript.auth.dto.LoginResponse;
import com.herbscript.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        CurrentUserResponse user = new CurrentUserResponse(1L, request.username(), "系统管理员", List.of("ADMIN"));
        LoginResponse response = new LoginResponse("mock-jwt-token", user);
        return ApiResponse.success(response);
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me() {
        return ApiResponse.success(new CurrentUserResponse(1L, "admin", "系统管理员", List.of("ADMIN")));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.success(null);
    }
}
