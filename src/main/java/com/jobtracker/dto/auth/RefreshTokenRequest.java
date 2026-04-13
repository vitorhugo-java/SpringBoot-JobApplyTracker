package com.jobtracker.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Token refresh request")
public record RefreshTokenRequest(
        @Schema(description = "Valid refresh token", example = "dGhpcyBpcyBhIHJlZnJlc2g...")
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {}
