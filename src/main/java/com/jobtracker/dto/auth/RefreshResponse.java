package com.jobtracker.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Refreshed access token response. New refresh token is sent via HttpOnly cookie.")
public record RefreshResponse(
        @Schema(description = "New JWT access token", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken
) {}
