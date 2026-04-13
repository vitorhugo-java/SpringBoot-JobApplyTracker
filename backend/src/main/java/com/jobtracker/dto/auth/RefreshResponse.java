package com.jobtracker.dto.auth;

public record RefreshResponse(
        String accessToken,
        String refreshToken
) {}
