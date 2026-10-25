package com.riseai.controller;

import com.riseai.dto.request.LoginRequest;
import com.riseai.dto.request.RefreshRequest;
import com.riseai.dto.request.RegisterRequest;
import com.riseai.dto.response.ResponseAPI;
import com.riseai.dto.response.TokenResponse;
import com.riseai.dto.response.UserResponse;
import com.riseai.security.AuthUser;
import com.riseai.service.AuthService;
import com.riseai.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {
    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseAPI<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseAPI.created(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseAPI<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseAPI.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseAPI<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseAPI.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseAPI<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
        return ResponseAPI.ok(null);
    }

    @GetMapping("/me")
    public ResponseAPI<UserResponse> me(@AuthenticationPrincipal AuthUser actor) {
        return ResponseAPI.ok(userService.me(actor));
    }
}
