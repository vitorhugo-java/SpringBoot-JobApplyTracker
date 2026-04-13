package com.jobtracker.dto.auth;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        UserResponse user
) {}
