package com.jobtracker.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Authentication response containing access token and user info. Refresh token is sent via HttpOnly cookie.")
public record AuthResponse(
        @Schema(description = "JWT access token", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,
        @Schema(description = "Authenticated user details")
        UserResponse user
) {}
