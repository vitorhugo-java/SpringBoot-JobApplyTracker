package com.jobtracker.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Authentication response containing tokens and user info")
public record AuthResponse(
        @Schema(description = "JWT access token", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,
        @Schema(description = "Refresh token for obtaining new access tokens", example = "dGhpcyBpcyBhIHJlZnJlc2g...")
        String refreshToken,
        @Schema(description = "Authenticated user details")
        UserResponse user
) {}
