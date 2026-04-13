package com.jobtracker.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Refreshed access token response")
public record RefreshResponse(
        @Schema(description = "New JWT access token", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,
        @Schema(description = "Refresh token (same or rotated)", example = "dGhpcyBpcyBhIHJlZnJlc2g...")
        String refreshToken
) {}
