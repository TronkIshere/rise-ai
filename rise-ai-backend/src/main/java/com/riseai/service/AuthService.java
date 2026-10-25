package com.riseai.service;

import com.riseai.dto.request.LoginRequest;
import com.riseai.dto.request.RefreshRequest;
import com.riseai.dto.request.RegisterRequest;
import com.riseai.dto.response.TokenResponse;
import com.riseai.dto.response.UserResponse;

public interface AuthService {
    UserResponse register(RegisterRequest request);

    TokenResponse login(LoginRequest request);

    TokenResponse refresh(RefreshRequest request);

    void logout(RefreshRequest request);
}
